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

print("=== ZERO-TRUST & FAIL-CLOSED SECURITY INTEGRATION VERIFICATION ===")

# 1. Acquire Tokens
_, s_body = req('http://localhost:8080/api/v1/auth/login', 'POST', {'username': 'shipper@soa8.org', 'password': 'ShipperPass123!'})
shipper_token = s_body['token']
_, p_body = req('http://localhost:8080/api/v1/auth/login', 'POST', {'username': 'planner@soa8.org', 'password': 'PlannerPass123!'})
planner_token = p_body['token']

# 2. Test Invalid / Expired / Missing JWT
print("\n--- Test 1: Missing JWT Rejected on Protected Decision Endpoint (401) ---")
try:
    req('http://localhost:8080/api/v1/assessments/test-id/approve', 'POST', {'plannerId': 'test'}, token=None)
    print("FAILED: Missing JWT was accepted!")
    exit(1)
except urllib.error.HTTPError as e:
    print(f"PASSED: Missing JWT rejected with HTTP {e.code}")
    assert e.code == 401

print("\n--- Test 2: Invalid / Garbage JWT Rejected on Protected Decision Endpoint (401) ---")
try:
    req('http://localhost:8080/api/v1/assessments/test-id/approve', 'POST', {'plannerId': 'test'}, token="invalid.token.structure")
    print("FAILED: Garbage JWT was accepted!")
    exit(1)
except urllib.error.HTTPError as e:
    print(f"PASSED: Garbage JWT rejected with HTTP {e.code}")
    assert e.code == 401

# 3. Test RBAC: Shipper cannot perform Planner-only action
print("\n--- Test 3: Shipper Forbidden from Planner Decisions (403) ---")
try:
    req('http://localhost:8080/api/v1/assessments/some-fake-id/approve', 'POST', {'plannerId': 'usr-shipper-001'}, token=shipper_token)
    print("FAILED: Shipper was allowed to call /approve!")
    exit(1)
except urllib.error.HTTPError as e:
    print(f"PASSED: Shipper role blocked from /approve with HTTP {e.code}")
    assert e.code == 403

# 4. Create a valid assessment and solve it to reach CANDIDATE_READY
print("\n--- Setting up live candidate for tamper & integrity tests ---")
decl_data = {
    'referenceNumber': f'DEC-SEC-{int(time.time())}',
    'shipperId': 'usr-shipper-001',
    'origin': 'JNPT Nhava Sheva',
    'destination': 'Delhi Freight Complex',
    'items': [
        {
            'cargoItemId': 'SEC-ITEM-01',
            'description': 'Secured Drum',
            'hazardClass': '3',
            'unNumber': 'UN1203',
            'lengthMm': 1000,
            'widthMm': 1000,
            'heightMm': 1000,
            'weightKg': 400,
            'stackable': True,
            'quantity': 1
        }
    ]
}
_, decl = req('http://localhost:8080/api/v1/declarations', 'POST', decl_data, shipper_token)
decl_id = decl['id']

comp = {
    'compartmentId': 'comp-sec-trailer',
    'name': 'Standard Trailer',
    'lengthMm': 12000,
    'widthMm': 2400,
    'heightMm': 2600,
    'maxPayloadKg': 25000
}
_, asmt = req('http://localhost:8080/api/v1/assessments', 'POST', {
    'declarationId': decl_id,
    'declarationVersion': 1,
    'ruleSnapshotId': 'snap-2026-q4-v1',
    'compartment': comp
}, planner_token)
asmt_id = asmt['id']

_, job = req('http://localhost:8080/api/v1/optimization-jobs', 'POST', {
    'declarationId': decl_id,
    'compartment': comp,
    'items': decl['items'],
    'ruleSnapshotId': 'snap-2026-q4-v1',
    'algorithm': 'HEURISTIC_EP_3D_V1',
    'async': False
}, planner_token)
job_id = job['jobId']
_, layout = req(f'http://localhost:8080/api/v1/optimization-jobs/{job_id}/result', 'GET', token=planner_token)
_, attached = req(f'http://localhost:8080/api/v1/assessments/{asmt_id}/candidate', 'POST', layout, planner_token)
real_digest = attached['candidateContentDigest']
real_version = attached['candidateLayoutVersion']
print(f"Candidate attached: version={real_version}, digest={real_digest[:16]}...")

# 5. Premature Approval Blocked (Solver OPTIMAL / FEASIBLE is NOT a substitute for validation)
print("\n--- Test 4: Premature Approval Blocked Before Validation (409) ---")
try:
    req(f'http://localhost:8080/api/v1/assessments/{asmt_id}/approve', 'POST', {
        'layoutVersion': real_version,
        'expectedDigest': real_digest,
        'plannerId': 'usr-planner-001',
        'notes': 'Premature approval before validation'
    }, planner_token)
    print("FAILED: Premature approval succeeded!")
    exit(1)
except urllib.error.HTTPError as e:
    print(f"PASSED: Approval blocked without independent validation: HTTP {e.code}")
    assert e.code == 409

# 6. Execute Independent Validation
_, snap = req('http://localhost:8080/api/v1/rule-snapshots/snap-2026-q4-v1', 'GET', token=planner_token)
val_req = {
    'layout': layout,
    'compartment': comp,
    'ruleSnapshot': snap
}
_, val_res = req('http://localhost:8080/api/v1/validations', 'POST', val_req, planner_token)
req(f'http://localhost:8080/api/v1/assessments/{asmt_id}/validation', 'POST', val_res, planner_token)
print(f"Validation executed and bound. Assessment in PENDING_APPROVAL state.")

# 7. Tampered Candidate Digest Blocks Approval
print("\n--- Test 5: Tampered Candidate Digest Blocks Approval (409) ---")
tampered_digest = "deadbeef" + real_digest[8:]
try:
    req(f'http://localhost:8080/api/v1/assessments/{asmt_id}/approve', 'POST', {
        'layoutVersion': real_version,
        'expectedDigest': tampered_digest,
        'plannerId': 'usr-planner-001',
        'notes': 'Tampered digest attack'
    }, planner_token)
    print("FAILED: Tampered digest approval succeeded!")
    exit(1)
except urllib.error.HTTPError as e:
    print(f"PASSED: Tampered digest blocked with HTTP {e.code}")
    assert e.code == 409

# 8. Stale Layout Version Blocks Approval
print("\n--- Test 6: Stale Layout Version Blocks Approval (409) ---")
try:
    req(f'http://localhost:8080/api/v1/assessments/{asmt_id}/approve', 'POST', {
        'layoutVersion': real_version + 99,
        'expectedDigest': real_digest,
        'plannerId': 'usr-planner-001',
        'notes': 'Stale version approval'
    }, planner_token)
    print("FAILED: Stale layout version approval succeeded!")
    exit(1)
except urllib.error.HTTPError as e:
    print(f"PASSED: Stale layout version blocked with HTTP {e.code}")
    assert e.code == 409

# 9. Incompatible Cargo Blocks Approval (Validation Fails -> VALIDATION_FAILED state)
print("\n--- Test 7: Incompatible Cargo Causes Validation Failure & Blocks Approval ---")
# Create incompatible cargo declaration (e.g. Class 1 Explosive + Class 3 Flammable Liquid)
incomp_decl = {
    'referenceNumber': f'DEC-INCOMP-{int(time.time())}',
    'shipperId': 'usr-shipper-001',
    'origin': 'JNPT Nhava Sheva',
    'destination': 'Delhi Freight Complex',
    'items': [
        {
            'cargoItemId': 'ITEM-EXPLOSIVE-01',
            'description': 'Class 1 Detonating Cord',
            'hazardClass': '1.1',
            'unNumber': 'UN0048',
            'lengthMm': 1000,
            'widthMm': 1000,
            'heightMm': 1000,
            'weightKg': 300,
            'stackable': False,
            'quantity': 1
        },
        {
            'cargoItemId': 'ITEM-FLAMMABLE-01',
            'description': 'Class 3 Solvent',
            'hazardClass': '3',
            'unNumber': 'UN1993',
            'lengthMm': 1000,
            'widthMm': 1000,
            'heightMm': 1000,
            'weightKg': 400,
            'stackable': False,
            'quantity': 1
        }
    ]
}
_, idecl = req('http://localhost:8080/api/v1/declarations', 'POST', incomp_decl, shipper_token)
_, iasmt = req('http://localhost:8080/api/v1/assessments', 'POST', {
    'declarationId': idecl['id'],
    'declarationVersion': 1,
    'ruleSnapshotId': 'snap-2026-q4-v1',
    'compartment': comp
}, planner_token)
iasmt_id = iasmt['id']

# Place them directly adjacent in the same compartment to trigger segregation failure
forced_placements = [
    {
        'placementId': 'pl-exp-1',
        'cargoItemId': 'ITEM-EXPLOSIVE-01',
        'packageId': 1,
        'x': 0, 'y': 0, 'z': 0,
        'lengthMm': 1000, 'widthMm': 1000, 'heightMm': 1000,
        'weightKg': 300,
        'hazardClass': '1.1',
        'unNumber': 'UN0048',
        'rotation': 'DEFAULT'
    },
    {
        'placementId': 'pl-flam-1',
        'cargoItemId': 'ITEM-FLAMMABLE-01',
        'packageId': 1,
        'x': 1000, 'y': 0, 'z': 0,
        'lengthMm': 1000, 'widthMm': 1000, 'heightMm': 1000,
        'weightKg': 400,
        'hazardClass': '3',
        'unNumber': 'UN1993',
        'rotation': 'DEFAULT'
    }
]
incomp_layout = {
    'candidateLayoutId': f'layout-incomp-{int(time.time())}',
    'layoutVersion': 1,
    'compartmentId': 'comp-sec-trailer',
    'solverStatus': 'FEASIBLE',
    'solverProvenance': 'FORCED_INCOMPATIBLE_TEST',
    'placements': forced_placements
}
_, attached_incomp = req(f'http://localhost:8080/api/v1/assessments/{iasmt_id}/candidate', 'POST', incomp_layout, planner_token)

# Validate incompatible candidate
incomp_val_req = {
    'layout': incomp_layout,
    'compartment': comp,
    'ruleSnapshot': snap
}
_, incomp_val_res = req('http://localhost:8080/api/v1/validations', 'POST', incomp_val_req, planner_token)
print(f"Independent Validation Result: Passed={incomp_val_res['passed']}, Findings={len(incomp_val_res.get('findings', []))}")
assert incomp_val_res['passed'] == False, "Expected validation to FAIL for Class 1 + Class 3 adjacent placement!"

# Record validation outcome on assessment
_, rec_incomp = req(f'http://localhost:8080/api/v1/assessments/{iasmt_id}/validation', 'POST', incomp_val_res, planner_token)
print(f"Assessment Workflow Status after failed validation: {rec_incomp['workflowStatus']}")
assert rec_incomp['workflowStatus'] == 'VALIDATION_FAILED'

# Attempt to approve the incompatible plan -> must fail closed
try:
    req(f'http://localhost:8080/api/v1/assessments/{iasmt_id}/approve', 'POST', {
        'layoutVersion': 1,
        'expectedDigest': attached_incomp['candidateContentDigest'],
        'plannerId': 'usr-planner-001',
        'notes': 'Attempting illegal approval of incompatible load'
    }, planner_token)
    print("FAILED: Incompatible cargo approval succeeded!")
    exit(1)
except urllib.error.HTTPError as e:
    print(f"PASSED: Incompatible cargo approval strictly BLOCKED by server-side validation gate: HTTP {e.code}")
    assert e.code == 409

print("\n>>> ALL ZERO-TRUST & FAIL-CLOSED CONTROLS VERIFIED SUCCESSFULLY! <<<")
