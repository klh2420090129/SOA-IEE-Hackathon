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

print("=== STEP 1: AUTHENTICATION ===")
_, s_body = req('http://localhost:8080/api/v1/auth/login', 'POST', {'username': 'shipper@soa8.org', 'password': 'ShipperPass123!'})
shipper_token = s_body['token']
_, p_body = req('http://localhost:8080/api/v1/auth/login', 'POST', {'username': 'planner@soa8.org', 'password': 'PlannerPass123!'})
planner_token = p_body['token']
print("Authenticated successfully.")

print("\n=== STEP 2: CREATE DECLARATION FOR REJECTION TEST ===")
decl_data = {
    'referenceNumber': f'DEC-REJ-{int(time.time())}',
    'shipperId': 'usr-shipper-001',
    'origin': 'Nhava Sheva Port',
    'destination': 'Dadri ICD Terminal',
    'items': [
        {
            'cargoItemId': 'ITEM-SOLVENT-01',
            'description': 'Industrial Solvent',
            'hazardClass': '3',
            'unNumber': 'UN1993',
            'lengthMm': 1200,
            'widthMm': 1000,
            'heightMm': 1000,
            'weightKg': 600,
            'stackable': True,
            'quantity': 1
        }
    ]
}
_, decl = req('http://localhost:8080/api/v1/declarations', 'POST', decl_data, shipper_token)
decl_id = decl['id']
print(f"Declaration created: ID={decl_id}, initial status={decl.get('status', 'SUBMITTED')}")

print("\n=== STEP 3: INITIALIZE ASSESSMENT ===")
compartment = {
    'compartmentId': 'comp-standard-40ft',
    'name': 'Standard 40ft High-Cube Trailer',
    'lengthMm': 12000,
    'widthMm': 2400,
    'heightMm': 2600,
    'maxPayloadKg': 25000
}
asmt_data = {
    'declarationId': decl_id,
    'declarationVersion': 1,
    'ruleSnapshotId': 'snap-2026-q4-v1',
    'compartment': compartment
}
_, asmt = req('http://localhost:8080/api/v1/assessments', 'POST', asmt_data, planner_token)
asmt_id = asmt['id']
print(f"Assessment created: ID={asmt_id}, Workflow={asmt['workflowStatus']}")

print("\n=== STEP 4: SOLVE & ATTACH CANDIDATE LAYOUT ===")
job_req = {
    'declarationId': decl_id,
    'compartment': compartment,
    'items': decl['items'],
    'ruleSnapshotId': 'snap-2026-q4-v1',
    'algorithm': 'HEURISTIC_EP_3D_V1',
    'async': False
}
_, job = req('http://localhost:8080/api/v1/optimization-jobs', 'POST', job_req, planner_token)
job_id = job["jobId"]
_, layout = req(f'http://localhost:8080/api/v1/optimization-jobs/{job_id}/result', 'GET', token=planner_token)
_, rec_asmt = req(f'http://localhost:8080/api/v1/assessments/{asmt_id}/candidate', 'POST', layout, planner_token)
print(f"Candidate attached: Status={rec_asmt['workflowStatus']}")

print("\n=== STEP 5: LOAD PLANNER OPERATIONAL REJECTION ===")
rej_req = {
    'layoutVersion': rec_asmt['candidateLayoutVersion'],
    'plannerId': 'usr-planner-001',
    'rejectionReasonCode': 'OPERATIONAL_CAPACITY_HOLD',
    'notes': 'Overdimensional route restriction along bridge route corridor'
}
_, rej_asmt = req(f'http://localhost:8080/api/v1/assessments/{asmt_id}/reject', 'POST', rej_req, planner_token)
print(f"Rejection completed: Status={rej_asmt['workflowStatus']}, Reason={rej_asmt.get('rejectionReasonCode')}")
assert rej_asmt['workflowStatus'] == 'REJECTED'

print("\n=== STEP 6: VERIFY PERSISTENCE OF REJECTION IN COMPARTMENT SERVICE ===")
_, reloaded_asmt = req(f'http://localhost:8080/api/v1/assessments/{asmt_id}', 'GET', token=planner_token)
print(f"Reloaded Assessment ID={reloaded_asmt['id']}: Status={reloaded_asmt['workflowStatus']}")
assert reloaded_asmt['workflowStatus'] == 'REJECTED'

print("\n=== STEP 7: VERIFY KAFKA EVENT PROPAGATION TO DECLARATION SERVICE ===")
# Wait up to 10 seconds for Transactional Outbox scheduler (every 1.5s-5s) to publish and CargoDeclaration consumer to process
projection_status = None
for i in range(15):
    time.sleep(1)
    _, proj = req(f'http://localhost:8080/api/v1/declarations/{decl_id}/workflow', 'GET', token=shipper_token)
    projection_status = proj.get('workflowStatus')
    if projection_status == 'REJECTED':
        print(f"Downstream Projection updated via Kafka! Attempt {i+1}: Status={projection_status}, Reason={proj.get('rejectionReason')}")
        break

assert projection_status == 'REJECTED', f"Expected projection status REJECTED, got {projection_status}, payload: {proj}"

print("\n=== STEP 8: VERIFY REJECTED CANDIDATE CANNOT SUBSEQUENTLY BE APPROVED ===")
appr_req = {
    'layoutVersion': reloaded_asmt['candidateLayoutVersion'],
    'expectedDigest': reloaded_asmt['candidateContentDigest'],
    'plannerId': 'usr-planner-001',
    'notes': 'Illegitimate approval attempt on rejected plan'
}
try:
    req(f'http://localhost:8080/api/v1/assessments/{asmt_id}/approve', 'POST', appr_req, planner_token)
    print("ERROR: Rejected assessment was allowed to approve!")
    exit(1)
except urllib.error.HTTPError as e:
    print(f"Approval of rejected assessment correctly BLOCKED by server-side state machine: HTTP {e.code}")
    assert e.code in (400, 409, 422), f"Expected client error code, got {e.code}"

print("\n>>> REJECTION & DOWNSTREAM KAFKA PROPAGATION VERIFIED SUCCESSFULLY! <<<")
