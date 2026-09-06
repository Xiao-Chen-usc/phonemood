#!/usr/bin/env python3
"""Validate v2.0 exports (draft.2 remains supported). Requires jsonschema >= 4.17; no Android dependency.

Usage: python3 scripts/validate_period_export.py path/to/export.json
Checks structure and selected cross-field invariants, not statistical correctness.
"""
import json
import sys
from datetime import datetime, timedelta
from pathlib import Path
from jsonschema import Draft202012Validator, FormatChecker


def timestamp(value):
    return datetime.fromisoformat(value.replace('Z', '+00:00'))


def duration(interval):
    value = int((timestamp(interval['end_utc_exclusive']) - timestamp(interval['start_utc'])).total_seconds() * 1000)
    assert value >= 0, 'Reversed interval'
    return value


def validate(data):
    if data['schema_version'] != '2.0':
        from validate_period_export_draft import validate as draft
        return draft(data)
    schema = json.loads((Path(__file__).resolve().parents[1] / 'docs/schemas/phonemood-period-v2.schema.json').read_text())
    Draft202012Validator.check_schema(schema)
    Draft202012Validator(schema, format_checker=FormatChecker()).validate(data)
    def index(items):
        result = {item['id']: item for item in items}
        assert len(result) == len(items), 'Duplicate IDs'
        return result
    apps = index(data['apps'])
    sessions = index(data['sessions'])
    observations = index(data['mood_observations'] + data['context']['prior_mood_observations'])
    segments = data['usage_segments']
    index(segments + data['context']['usage_segments'])
    start = timestamp(data['period']['range']['start_utc'])
    end = timestamp(data['period']['range']['end_utc_exclusive'])
    for segment in segments:
        assert start <= timestamp(segment['range']['start_utc']) <= timestamp(segment['range']['end_utc_exclusive']) <= end
        assert segment['app_id'] in apps and segment['session_id'] in sessions
    def check_apps(items):
        assert len({a['app_id'] for a in items}) == len(items)
        assert all(a['app_id'] in apps for a in items)
        return sum(a['active_ms'] for a in items)
    total = sum(duration(s['range']) for s in segments)
    assert total == data['summary']['recorded_active_ms'] == check_apps(data['summary']['apps'])
    for app in data['summary']['apps']:
        assert app['active_ms'] == sum(duration(s['range']) for s in segments if s['app_id'] == app['app_id'])
    assert len(data['days']) == data['period']['calendar_days']
    assert sum(day['recorded_active_ms'] or 0 for day in data['days']) == total
    for i, day in enumerate(data['days']):
        assert datetime.fromisoformat(day['date']) == datetime.fromisoformat(data['period']['start_date']) + timedelta(days=i)
        coverage = day['coverage']
        assert sum(coverage[k] for k in ('verified_ms', 'unknown_ms', 'not_monitored_ms')) == duration(day['range'])
        assert check_apps(day['apps']) == (day['recorded_active_ms'] or 0)
    answered = {key: o for key, o in observations.items() if o['response'] is not None and start <= timestamp(o['response']['answered_at_utc']) < end}
    rows = data['analysis_matrix']['rows']
    assert len(rows) == len(answered) and {r['observation_id'] for r in rows} == set(answered)
    for row in rows:
        answer = answered[row['observation_id']]['response']
        assert row['mood_score'] == answer['score']
        assert row['answered_at_utc'] == answer['answered_at_utc'] == row['window']['end_utc_exclusive']
        assert duration(row['window']) == 1800000
        assert check_apps(row['apps']) == (row['phone_active_ms'] or 0)
        assert row['phone_active_ms'] is None or row['phone_active_ms'] <= 1800000
        if row.get('prior_response') is not None:
            prior = observations[row['prior_response']['observation_id']]['response']
            assert prior is not None and timestamp(prior['answered_at_utc']) < timestamp(row['answered_at_utc'])
    assert len(answered) == data['data_quality']['period_answer_count'] == data['summary']['mood']['n_answers']
    expected_mean = sum(o['response']['score'] for o in answered.values()) / len(answered) if answered else None
    assert data['summary']['mood']['mean_score'] == expected_mean
    findings = index(data['statistical_analysis']['findings'])
    top = data['statistical_analysis']['top_app_finding_ids']
    assert len(top) <= 3 and len(top) == len(set(top))
    assert all(findings[key]['kind'] == 'APP_USAGE' and findings[key]['status'] in ('EARLY_HIGHER', 'EARLY_LOWER') for key in top)


    transitions = index(data['transitions'])
    all_segments = segments + data['context']['usage_segments']
    def clipped_total(interval):
        a = timestamp(interval['start_utc']); b = timestamp(interval['end_utc_exclusive'])
        return sum(max(0, round((min(timestamp(s['range']['end_utc_exclusive']), b) - max(timestamp(s['range']['start_utc']), a)).total_seconds()*1000)) for s in all_segments)
    for row in rows:
        assert row['session_id'] in sessions
        assert row['phone_active_ms'] == clipped_total(row['window'])
        assert sum(row['coverage'][k] for k in ('verified_ms','unknown_ms','not_monitored_ms')) == duration(row['window'])
    for t in transitions.values():
        a = observations[t['start_observation_id']]; b = observations[t['end_observation_id']]
        assert t['start_score'] == a['response']['score'] and t['end_score'] == b['response']['score']
        assert t['mood_delta'] == t['end_score'] - t['start_score']
        assert t['range']['start_utc'] == a['response']['answered_at_utc'] and t['range']['end_utc_exclusive'] == b['response']['answered_at_utc']
        assert t['elapsed_ms'] == duration(t['range']) == t['phone_active_ms'] + t['non_active_ms']
        assert check_apps(t['apps']) == t['phone_active_ms'] == clipped_total(t['range'])
        assert sum(t['coverage'][k] for k in ('verified_ms','unknown_ms','not_monitored_ms')) == t['elapsed_ms']
        assert t['usable'] == (not t['exclusion_reasons'])
        if t['usable']:
            assert a['session_id'] == b['session_id'] == t['session_id']
            assert 0 < t['elapsed_ms'] <= 7200000 and t['coverage']['status'] == 'COMPLETE'
    models = index(data['statistical_analysis']['models'])
    for model in models.values():
        k = len(model['terms']); assert k == len(model['coefficients'])
        assert not model['covariance'] or len(model['covariance']) == k and all(len(r) == k for r in model['covariance'])
        universe = set(d['date'] for d in data['days'] if d['eligible_for_daily_statistics']) if model['outcome'] == 'DAILY_MINUTES' else set(transitions) if model['outcome'] == 'END_MOOD_SCORE' else set(answered)
        assert set(model['sample_ids']) <= universe
        assert len(model['sample_ids']) == len(set(model['sample_ids']))
    for row in rows + list(transitions.values()):
        key = row.get('observation_id', row.get('id'))
        for member in row['model_membership']:
            assert member['model_id'] in models
            assert member['included'] == (key in models[member['model_id']]['sample_ids'])
    for finding in findings.values():
        assert finding['model_id'] in models
        if finding['app_id'] is not None: assert finding['app_id'] in apps
        assert finding['comparison_unit'] == ('DAYS' if finding['kind'] == 'DAILY_USE_TREND' else 'MINUTES')
        if finding['sensitivity_model_id'] is not None: assert finding['sensitivity_model_id'] in models
        if finding['block_refits']:
            assert finding['consistency'] == sum(r['agrees'] for r in finding['block_refits']) / len(finding['block_refits'])
    assert all(item['finding_id'] in findings for item in data['user_insights'])
    cursor = data['period']['range']['start_utc']
    for interval in data['data_quality']['coverage_intervals']:
        assert interval['range']['start_utc'] == cursor
        cursor = interval['range']['end_utc_exclusive']
    assert cursor == data['period']['range']['end_utc_exclusive']

if __name__ == '__main__':
    for name in sys.argv[1:]:
        validate(json.loads(Path(name).read_text(encoding='utf-8')))
        print('Validated ' + name)
