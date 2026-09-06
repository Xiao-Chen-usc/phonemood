#!/usr/bin/env python3
"""Validate draft exports. Requires jsonschema >= 4.17; no Android dependency.

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
    schema = json.loads((Path(__file__).resolve().parents[1] / 'docs/schemas/phonemood-period-v2-draft2.schema.json').read_text())
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
        if row['prior_response'] is not None:
            prior = observations[row['prior_response']['observation_id']]['response']
            assert prior is not None and timestamp(prior['answered_at_utc']) < timestamp(row['answered_at_utc'])
    assert len(answered) == data['data_quality']['period_answer_count'] == data['summary']['mood']['n_answers']
    expected_mean = sum(o['response']['score'] for o in answered.values()) / len(answered) if answered else None
    assert data['summary']['mood']['mean_score'] == expected_mean
    findings = index(data['statistical_analysis']['findings'])
    top = data['statistical_analysis']['top_factor_ids']
    assert len(top) <= 3 and len(top) == len(set(top))
    assert all(findings[key]['factor_type'] == 'APP_USAGE' and findings[key]['eligible_for_top_factors'] for key in top)


if __name__ == '__main__':
    validate(json.loads(Path(sys.argv[1]).read_text(encoding='utf-8')))
    print('Validated JSON structure and selected period/observation invariants.')
