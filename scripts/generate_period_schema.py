#!/usr/bin/env python3
"""Build the reviewed v2.0 contract. No schema inference from example data."""
import json
from pathlib import Path

S = {'type': 'string'}
B = {'type': 'boolean'}
I = {'type': 'integer'}
N = {'type': 'number'}
U = {'type': 'integer', 'minimum': 0}
T = {'type': 'string', 'format': 'date-time'}
D = {'type': 'string', 'format': 'date'}
P = {'type': 'number', 'minimum': 0, 'maximum': 1}
def obj(**properties):
    return {'type': 'object', 'properties': properties, 'required': list(properties), 'additionalProperties': False}
def arr(items):
    return {'type': 'array', 'items': items}
def ref(name):
    return {'$ref': '#/$defs/' + name}
def nullable(value):
    return {'anyOf': [value, {'type': 'null'}]}
def enum(*values):
    return {'enum': list(values)}
def const(value):
    return {'const': value}

defs = {}
defs['range'] = obj(start_utc=T, end_utc_exclusive=T)
defs['app_time'] = obj(app_id=S, active_ms=U)
apps = arr(ref('app_time'))
defs['coverage'] = obj(status=enum('COMPLETE', 'PARTIAL', 'NOT_OBSERVED', 'UNKNOWN'), verified_ms=U,
                       unknown_ms=U, not_monitored_ms=U, reasons=arr(S))
defs['coverage_interval'] = obj(range=ref('range'), state=enum('VERIFIED', 'UNKNOWN', 'NOT_MONITORED'), reason=S)
defs['segment'] = obj(id=S, source_id=S, range=ref('range'), app_id=S, session_id=S, observed_zone_id=S, zone_inferred=B)
defs['observation'] = obj(id=S, session_id=S, scope=enum('PERIOD', 'CONTEXT_ONLY'), checkpoint_at_utc=T,
    checkpoint_active_minutes=U, notification_at_utc=nullable(T), overlay_first_shown_at_utc=nullable(T), response_state=S,
    response=nullable(obj(answered_at_utc=T, answer_zone_id=S, score=I, latency_from_checkpoint_ms=I,
                          latency_from_first_delivery_ms=nullable(I))))
defs['membership'] = obj(model_id=S, included=B, exclusion_reasons=arr(S))
members = arr(ref('membership'))
defs['row'] = obj(observation_id=S, session_id=S, answered_at_utc=T, report_date=D, mood_score=I,
    window=ref('range'), coverage=ref('coverage'), phone_active_ms=U, apps=apps, session_active_ms_at_answer=U,
    session_coverage_complete=B, quality_flags=arr(S), model_membership=members)
defs['transition'] = obj(id=S, start_observation_id=S, end_observation_id=S, session_id=S, range=ref('range'),
    start_score=I, end_score=I, mood_delta=I, elapsed_ms=U, phone_active_ms=U, non_active_ms=I, apps=apps,
    coverage=ref('coverage'), usable=B, exclusion_reasons=arr(S), model_membership=members)
defs['model'] = obj(id=S, outcome=enum('DAILY_MINUTES', 'MOOD_SCORE', 'MOOD_SCORE_WITHIN_SESSION', 'END_MOOD_SCORE'),
    status=enum('OK', 'NOT_ESTIMABLE'), sample_ids=arr(S), terms=arr(S), coefficients=arr(N), covariance=arr(arr(N)),
    degrees_of_freedom=nullable(N), standard_error_method=nullable(enum('HC3', 'DAY_CLUSTER_CR1')),
    dropped_controls=arr(S), warnings=arr(S))
defs['finding'] = obj(id=S, model_id=S, kind=enum('DAILY_USE_TREND', 'SESSION_LENGTH', 'APP_USAGE'),
    app_id=nullable(S), status=enum('INSUFFICIENT_DATA', 'NO_NOTICEABLE_TENDENCY', 'MIXED_TENDENCY', 'EARLY_HIGHER', 'EARLY_LOWER'),
    difference=nullable(N), comparison_value=nullable(N), n=U, groups=U, consistency=nullable(P), ci_low=nullable(N),
    ci_high=nullable(N), p=nullable(P), q=nullable(P), template_key=S, reasons=arr(S), sensitivity_model_id=nullable(S),
    block_refits=arr(obj(excluded_block=S, difference=nullable(N), agrees=B, status=enum('OK', 'NOT_ESTIMABLE'))),
    comparison_unit=enum('DAYS', 'MINUTES'), contrast_outcome=enum('DAILY_MINUTES_CHANGE', 'WITHIN_SESSION_MOOD_CHANGE', 'EXTRA_MOOD_CHANGE'))
defs['policy'] = obj(minimum_ratings=const(20), max_pair_ms=const(7200000), max_response_latency_ms=const(300000),
    minimum_complete_trend_days=const(3), near_zero_mood_points=const(.2), block_consistency_threshold=const(.8),
    app_min_exposed_rows=const(5), app_min_matched_rows=const(10), app_min_comparison_minutes=const(2),
    app_match_total_minutes_tolerance=const(5), app_match_elapsed_minutes_tolerance=const(10), app_match_start_score_tolerance=const(1),
    app_comparison=S, time_sensitivity_min_rows=const(40), time_sensitivity_min_days=const(7), time_sensitivity_min_4h_bins=const(3),
    require_significance_for_display=const(False), require_fdr_for_display=const(False), solver=const('SVD_SCALED_INDEPENDENT_COLUMNS'),
    rank_tolerance=const(1e-9), daily_min_change_minutes=const(15), daily_relative_change_threshold=const(.1), contrast_cap_minutes=const(30),
    cluster_min_days=const(20), minimum_blocks_for_deletion_check=const(3), max_condition_number=const(1e8),
    session_weight=const('ONE_OVER_SESSION_RATING_COUNT'), block_selection=const('DAY_IF_AT_LEAST_3_ELSE_SESSION'), daily_drift_sensitivity=const('NOT_IMPLEMENTED_IN_V1'), sampling=S)

schema = obj(record_type=const('phonemood_period_export'), schema_version=const('2.0'), calculation_version=const('2.0'), analysis_version=const('1.0'),
    export_metadata=obj(generated_at_utc=T, source_revision=U, source_app_version=S, data_origin=enum('USER_RECORDS', 'SYNTHETIC_TEST_FIXTURE')),
    period=obj(calendar_days=enum(1, 7, 30), start_date=D, end_date_inclusive=D, reporting_zone_id=S, range=ref('range'), includes_ongoing_day=B),
    measurement=obj(mood_scale=obj(min=const(1), max=const(10), higher_is_better=const(True)), exposure_window_ms=const(1800000),
        exposure_anchor=const('ANSWER_TIME'), app_model_window=const('BETWEEN_ANSWERS'), interval_convention=const('START_INCLUSIVE_END_EXCLUSIVE'),
        prompt_sampling=const('ACTIVE_USE_THRESHOLD'), daily_summary_population=const('COMPLETE_FINISHED_DAYS'), percentile_method=const('LINEAR_TYPE_7'),
        default_settings=obj(mood_interval_minutes=const(30), session_reset_minutes=const(5), excluded_packages=arr(S)),
        configuration_events=arr(obj(at_utc=T, setting=S, old_value=S, new_value=S))),
    data_quality=obj(complete_finished_days=U, days_with_records=U, period_answer_count=U, valid_rating_count=U, delivered_prompt_count=U,
        answered_delivered_prompt_count=U, response_rate=nullable(P), response_rate_definition=S, coverage_intervals=arr(ref('coverage_interval')), warnings=arr(S)),
    summary=obj(recorded_active_ms=U, apps=apps, daily_statistics=obj(n_days=U, dates=arr(D), mean_active_ms=nullable(N),
        median_active_ms=nullable(N), p25_active_ms=nullable(N), p75_active_ms=nullable(N)), mood=obj(n_answers=U, mean_score=nullable(N))),
    days=arr(obj(date=D, range=ref('range'), is_ongoing=B, coverage=ref('coverage'), recorded_active_ms=nullable(U), apps=apps,
        answered_mood_count=U, mean_mood_score=nullable(N), eligible_for_daily_statistics=B)),
    apps=arr(obj(id=S, display_name=S)), usage_segments=arr(ref('segment')),
    sessions=arr(obj(id=S, start_utc=T, end_utc=nullable(T), status=S, active_ms_in_period=U, active_ms_at_snapshot=U, started_before_period=B)),
    mood_observations=arr(ref('observation')),
    context=obj(range=nullable(ref('range')), usage_segments=arr(ref('segment')), coverage_intervals=arr(ref('coverage_interval')),
        prior_mood_observations=arr(ref('observation')), inclusion_reason=S),
    analysis_matrix=obj(row_unit=const('ONE_ANSWERED_MOOD_OBSERVATION_IN_PERIOD'), rows=arr(ref('row'))),
    transitions=arr(ref('transition')),
    statistical_analysis=obj(policy_version=const('1.0'), models=arr(ref('model')), findings=arr(ref('finding')), top_app_finding_ids=arr(S), policy_snapshot=ref('policy')),
    user_insights=arr(obj(finding_id=S, template_key=S, arguments=obj(difference=nullable(N), comparison_value=nullable(N), comparison_unit=enum('DAYS', 'MINUTES'), observations=U, app_id=nullable(S)))),
    data_dictionary=obj(definitions={'type': 'object', 'additionalProperties': S}, limitations=arr(S)))
schema.update({'$schema': 'https://json-schema.org/draft/2020-12/schema', '$id': 'https://phonemood.local/schemas/period-v2.0.json',
               'title': 'PhoneMood period export 2.0', '$defs': defs})
if __name__ == '__main__':
    target = Path(__file__).resolve().parents[1] / 'docs/schemas/phonemood-period-v2.schema.json'
    target.write_text(json.dumps(schema, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
