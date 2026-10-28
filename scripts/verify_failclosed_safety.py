import urllib.request
import json
import time

def req(url, method='GET', data=None, token=None):
    headers = {'Content-Type': 'application/json', 'Accept': 'application/json'}
    if token:
        headers['Authorization'] = f'Bearer {token}'
    bdata = json.dumps(data).encode('utf-8') if data else None
    r = urllib.request.Request(url, data=bdata, headers=headers, method=method)
    with urllib.request.urlopen(r) as res:
        return res.status, json.loads(res.read().decode('utf-8'))

print("=== VERIFY FAIL-CLOSED SAFETY & AUTHORIZATION ===")

# 1. Login
_, s_body = req('http://localhost:8080/api/v1/auth/login', 'POST', {'username': 'shipper@soa8.org', 'password': 'ShipperPass123!'})
shipper_token = s_body['token']
_, p_body = req('http://localhost:8080/api/v1/auth/login', 'POST', {'username': 'planner@soa8.org', 'password': 'PlannerPass123!'})
planner_token = p_body['token']
print("1. Authentication tokens acquired.")

# Create declaration
decl_data = {
    'referenceNumber': f'DEC-FAILSAFE-{int(time.time())}',
    'shipperId': 'usr-shipper-001',
    'origin': 'JNPT Mumbai',
    'destination': 'Dadri ICD',
    'items': [{
        'cargoItemId': 'ITEM-SAFE-01',
        'description': 'Safe Cargo',
        'hazardClass': '3',
        'unNumber': 'UN1993',
        'lengthMm': 1000,
        'widthMm': 1000,
        'heightMm': 1000,
        'weightKg': 200,
        'stackable': True,
        'quantity': 1
    }]
}
_, decl = req('http://localhost:8080/api/v1/declarations', 'POST', decl_data, shipper_token)
decl_id = decl['id']

compartment = {
    'compartmentId': 'comp-fail-safe',
    'name': 'Standard Trailer',
    'lengthMm': 12000,
    'widthMm': 2400,
    'heightMm': 2600,
    'maxPayloadKg': 25000
}

# 2. Missing or unavailable mandatory rule snapshot blocks optimization
try:
    req('http://localhost:8080/api/v1/optimization-jobs', 'POST', {
        'declarationId': decl_id,
        'compartment': compartment,
        'items': decl['items'],
        'ruleSnapshotId': 'non-existent-snapshot-xyz',
        'algorithm': 'HEURISTIC_EP_3D_V1'
    }, planner_token)
    print("FAIL: Missing rule snapshot allowed optimization!")
    exit(1)
except urllib.error.HTTPError as e:
    print(f"PASS: Missing rule snapshot blocks optimization with HTTP {e.code}")
    assert e.code == 422

# Create valid assessment
_, asmt = req('http://localhost:8080/api/v1/assessments', 'POST', {
    'declarationId': decl_id,
    'declarationVersion': 1,
    'ruleSnapshotId': 'snap-2026-q4-v1',
    'compartment': compartment
}, planner_token)
asmt_id = asmt['id']

# 3. Shipper cannot perform planner-only actions (approve / reject)
try:
    req(f'http://localhost:8080/api/v1/assessments/{asmt_id}/approve', 'POST', {
        'layoutVersion': 1,
        'expectedDigest': 'some_hash',
        'plannerId': 'usr-shipper-001'
    }, shipper_token)
    print("FAIL: Shipper was allowed to approve!")
    exit(1)
except urllib.error.HTTPError as e:
    print(f"PASS: Shipper approve blocked with HTTP {e.code}")
    assert e.code in (401, 403)

try:
    req(f'http://localhost:8080/api/v1/assessments/{asmt_id}/reject', 'POST', {
        'layoutVersion': 1,
        'plannerId': 'usr-shipper-001',
        'rejectionReasonCode': 'TEST'
    }, shipper_token)
    print("FAIL: Shipper was allowed to reject!")
    exit(1)
except urllib.error.HTTPError as e:
    print(f"PASS: Shipper reject blocked with HTTP {e.code}")
    assert e.code in (401, 403)

# 4. Invalid or missing JWTs are rejected on authoritative decision endpoints
try:
    req(f'http://localhost:8080/api/v1/assessments/{asmt_id}/approve', 'POST', {
        'layoutVersion': 1,
        'expectedDigest': 'abc',
        'plannerId': 'usr-planner-001'
    }, token='invalid.bearer.token')
    print("FAIL: Invalid JWT accepted!")
    exit(1)
except urllib.error.HTTPError as e:
    print(f"PASS: Invalid JWT blocked with HTTP {e.code}")
    assert e.code == 401

try:
    req(f'http://localhost:8080/api/v1/assessments/{asmt_id}/approve', 'POST', {
        'layoutVersion': 1,
        'expectedDigest': 'abc',
        'plannerId': 'usr-planner-001'
    })
    print("FAIL: Missing JWT accepted!")
    exit(1)
except urllib.error.HTTPError as e:
    print(f"PASS: Missing JWT blocked with HTTP {e.code}")
    assert e.code == 401

# 5. Solver run to produce layout
job_req = {
    'declarationId': decl_id,
    'compartment': compartment,
    'items': decl['items'],
    'ruleSnapshotId': 'snap-2026-q4-v1',
    'algorithm': 'HEURISTIC_EP_3D_V1',
    'async': False
}
_, job = req('http://localhost:8080/api/v1/optimization-jobs', 'POST', job_req, planner_token)
job_id = job['jobId']
_, layout = req(f'http://localhost:8080/api/v1/optimization-jobs/{job_id}/result', 'GET', token=planner_token)
_, rec_asmt = req(f'http://localhost:8080/api/v1/assessments/{asmt_id}/candidate', 'POST', layout, planner_token)

# 6. Solver status is not treated as a substitute for independent validation
try:
    req(f'http://localhost:8080/api/v1/assessments/{asmt_id}/approve', 'POST', {
        'layoutVersion': rec_asmt['candidateLayoutVersion'],
        'expectedDigest': layout['contentDigest'],
        'plannerId': 'usr-planner-001',
        'notes': 'Premature approval attempt without validation'
    }, planner_token)
    print("FAIL: Candidate approved without validation!")
    exit(1)
except urllib.error.HTTPError as e:
    print(f"PASS: Approval without validation blocked with HTTP {e.code}")
    assert e.code in (400, 409, 422)

# 7. Tampered candidate digest blocks approval
# First run real independent validation
_, snap = req('http://localhost:8080/api/v1/rule-snapshots/snap-2026-q4-v1', 'GET', token=planner_token)
val_req = {'layout': layout, 'compartment': compartment, 'ruleSnapshot': snap}
_, val_res = req('http://localhost:8080/api/v1/validations', 'POST', val_req, planner_token)
req(f'http://localhost:8080/api/v1/assessments/{asmt_id}/validation', 'POST', val_res, planner_token)

try:
    req(f'http://localhost:8080/api/v1/assessments/{asmt_id}/approve', 'POST', {
        'layoutVersion': rec_asmt['candidateLayoutVersion'],
        'expectedDigest': '0000000000000000000000000000000000000000000000000000000000000000', # TAMPERED
        'plannerId': 'usr-planner-001',
        'notes': 'Tampered digest check'
    }, planner_token)
    print("FAIL: Tampered digest allowed approval!")
    exit(1)
except urllib.error.HTTPError as e:
    print(f"PASS: Tampered digest blocked with HTTP {e.code}")
    assert e.code in (400, 409, 422)

# 8. Stale layout version blocks approval
try:
    req(f'http://localhost:8080/api/v1/assessments/{asmt_id}/approve', 'POST', {
        'layoutVersion': 999, # STALE / INVALID VERSION
        'expectedDigest': layout['contentDigest'],
        'plannerId': 'usr-planner-001',
        'notes': 'Stale version check'
    }, planner_token)
    print("FAIL: Stale layout version allowed approval!")
    exit(1)
except urllib.error.HTTPError as e:
    print(f"PASS: Stale layout version blocked with HTTP {e.code}")
    assert e.code in (400, 409, 422)

# 9. Incompatible cargo cannot be approved (authoritative validation failure fails closed)
incompat_layout = dict(layout)
incompat_layout.pop('contentDigest', None)
incompat_layout['placements'] = [
    {'cargoItemId': 'IT-EXP', 'hazardClass': '1', 'xMm': 0, 'yMm': 0, 'zMm': 0, 'lengthMm': 1000, 'widthMm': 1000, 'heightMm': 1000, 'weightKg': 500, 'stackable': False, 'maxStackWeightKg': 0, 'orientation': 'DEFAULT'},
    {'cargoItemId': 'IT-FLAM', 'hazardClass': '3', 'xMm': 1000, 'yMm': 0, 'zMm': 0, 'lengthMm': 1000, 'widthMm': 1000, 'heightMm': 1000, 'weightKg': 500, 'stackable': False, 'maxStackWeightKg': 0, 'orientation': 'DEFAULT'}
]
_, incompat_val = req('http://localhost:8080/api/v1/validations', 'POST', {
    'layout': incompat_layout,
    'compartment': compartment,
    'ruleSnapshot': snap
}, planner_token)

print(f"Incompatible validation outcome: passed={incompat_val['passed']}, findings={len(incompat_val['findings'])}")
assert incompat_val['passed'] is False

# Create separate assessment to test failed validation binding
_, asmt_incompat = req('http://localhost:8080/api/v1/assessments', 'POST', {
    'declarationId': decl_id,
    'declarationVersion': 1,
    'ruleSnapshotId': 'snap-2026-q4-v1',
    'compartment': compartment
}, planner_token)
asmt_incompat_id = asmt_incompat['id']

_, attached_incompat = req(f'http://localhost:8080/api/v1/assessments/{asmt_incompat_id}/candidate', 'POST', incompat_layout, planner_token)
_, bound_incompat = req(f'http://localhost:8080/api/v1/assessments/{asmt_incompat_id}/validation', 'POST', incompat_val, planner_token)
print(f"Assessment status after failed validation bound: {bound_incompat['workflowStatus']}")
assert bound_incompat['workflowStatus'] == 'VALIDATION_FAILED'

# Attempt to approve candidate with failed validation
try:
    req(f'http://localhost:8080/api/v1/assessments/{asmt_incompat_id}/approve', 'POST', {
        'layoutVersion': attached_incompat['candidateLayoutVersion'],
        'expectedDigest': attached_incompat['candidateContentDigest'],
        'plannerId': 'usr-planner-001'
    }, planner_token)
    print("FAIL: Failed validation allowed approval!")
    exit(1)
except urllib.error.HTTPError as e:
    print(f"PASS: Failed validation correctly blocks approval with HTTP {e.code}")
    assert e.code in (400, 409, 422)

print("\n>>> ALL FAIL-CLOSED SAFETY & AUTHORIZATION CONTROLS VERIFIED WITH ZERO-TRUST EVIDENCE! <<<")
