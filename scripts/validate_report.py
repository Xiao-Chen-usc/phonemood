#!/usr/bin/env python3
"""Validate a PhoneMood export using only the Python standard library."""
import collections
import datetime as dt
import json
import sys

def validate(record):
    required = {'schema_version', 'record_type', 'date', 'generated_at', 'measurement', 'daily_summary', 'app_usage', 'sessions', 'timeline', 'mood_contexts', 'data_quality'}
    assert required <= record.keys(), f'Missing keys: {required - record.keys()}'
    assert record['schema_version'] == '1.0'
    assert record['record_type'] == 'daily_phone_usage_and_mood'
    def stamp(value):
        return dt.datetime.fromisoformat(value.replace('Z', '+00:00'))
    start = stamp(record['measurement']['day_start']['utc'])
    end = stamp(record['measurement']['day_end_exclusive']['utc'])
    apps = collections.Counter()
    sessions = collections.Counter()
    prior_end = start
    for segment in record['timeline']:
        a, b = stamp(segment['start']['utc']), stamp(segment['end']['utc'])
        assert start <= a <= b <= end, 'Segment outside reporting day'
        assert a >= prior_end, 'Overlapping timeline segments'
        assert round((b - a).total_seconds() * 1000) == segment['duration_ms']
        prior_end = b
        apps[segment['package_name']] += segment['duration_ms']
        sessions[segment['session_id']] += segment['duration_ms']
    assert sum(apps.values()) == record['daily_summary']['active_duration_ms']
    assert dict(apps) == {a['package_name']: a['active_duration_ms'] for a in record['app_usage']}
    for session in record['sessions']:
        assert sessions[session['session_id']] == session['active_duration_ms_in_day']
    checkpoints = record['mood_contexts']
    assert len({c['checkpoint_id'] for c in checkpoints}) == len(checkpoints)
    for checkpoint in checkpoints:
        assert start <= stamp(checkpoint['prompt_timestamp']['utc']) < end
        if checkpoint['response']:
            assert 1 <= checkpoint['response']['score'] <= 10
            assert checkpoint['response_status'] == 'ANSWERED'
    assert len(checkpoints) == record['daily_summary']['checkpoint_count']
    assert sum(c['response'] is not None for c in checkpoints) == record['daily_summary']['answered_count']
    return record['daily_summary']['active_duration_ms']

if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('Usage: python3 scripts/validate_report.py path/to/YYYY-MM-DD-phone-mood.json')
    with open(sys.argv[1], encoding='utf-8') as source:
        record = json.load(source)
    total = validate(record)
    print(f"Valid PhoneMood record: {record['date']}, {total / 60000:.2f} active minutes")
