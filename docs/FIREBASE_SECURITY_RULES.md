# Firebase Realtime Database Security Rules

> ⚠️ **DO NOT DEPLOY THIS RULESET AS WRITTEN — it is not the deployed source of
> truth.** It is kept for reference only.
>
> These rules key role checks on `root.child('users').child(auth.uid)`, but that
> mapping does not exist in this system: the Android app signs in
> **anonymously** (so `auth.uid` is an anonymous UID, never `driver_001`), and
> the ESP32 firmware holds **no credentials at all**. Applying these rules would
> lock out both the phone app and the hardware.
>
> The version-controlled, deployable rules live in [`database.rules.json`](../database.rules.json),
> with rollout instructions and the list of remaining gaps in
> [`FIREBASE_RULES_DEPLOYMENT.md`](FIREBASE_RULES_DEPLOYMENT.md).

## Production Security Rules for Smart Ambulance System

These rules provide secure access control based on user authentication and roles while allowing the necessary data access for the Smart Ambulance system.

## Security Rules

```json
{
  "rules": {
    // Users collection - role-based access
    "users": {
      ".read": "auth != null",
      "$userId": {
        // Users can read their own data
        ".read": "auth.uid == $userId || root.child('users').child(auth.uid).child('role').val() == 'admin'",
        // Users can update their own data, admins can update any user
        ".write": "auth.uid == $userId || root.child('users').child(auth.uid).child('role').val() == 'admin'"
      }
    },
    
    // Ambulances - driver and admin access
    "ambulances": {
      ".read": "auth != null",
      "$ambulanceId": {
        // Driver of this ambulance or admin can read/write
        ".read": "auth != null && (root.child('users').child(auth.uid).child('ambulanceId').val() == $ambulanceId || root.child('users').child(auth.uid).child('role').val() == 'admin')",
        ".write": "auth != null && (root.child('users').child(auth.uid).child('ambulanceId').val() == $ambulanceId || root.child('users').child(auth.uid).child('role').val() == 'admin')"
      }
    },
    
    // Emergency trips - all authenticated users can read, involved parties can write
    "emergencyTrips": {
      ".read": "auth != null",
      "$tripId": {
        // Driver, hospital, or admin involved can write
        ".write": "auth != null && (root.child('emergencyTrips').child($tripId).child('driverId').val() == auth.uid || root.child('emergencyTrips').child($tripId).child('destinationHospitalId').val() == root.child('users').child(auth.uid).child('hospitalId').val() || root.child('users').child(auth.uid).child('role').val() == 'admin')"
      }
    },
    
    // Junctions - police and admin access
    "junctions": {
      ".read": "auth != null",
      "$junctionId": {
        // Police assigned to this junction or admin can read/write
        ".read": "auth != null && (root.child('users').child(auth.uid).child('assignedJunctionId').val() == $junctionId || root.child('users').child(auth.uid).child('role').val() == 'admin')",
        ".write": "auth != null && (root.child('users').child(auth.uid).child('assignedJunctionId').val() == $junctionId || root.child('users').child(auth.uid).child('role').val() == 'admin')"
      }
    },
    
    // Junction events - all authenticated users can read/write
    "junctionEvents": {
      ".read": "auth != null",
      ".write": "auth != null"
    },
    
    // LoRa telemetry - all authenticated users can read/write
    "loraTelemetry": {
      ".read": "auth != null",
      ".write": "auth != null"
    },
    
    // Hospitals - all authenticated users can read, admins can write
    "hospitals": {
      ".read": "auth != null",
      "$hospitalId": {
        ".write": "auth != null && root.child('users').child(auth.uid).child('role').val() == 'admin'"
      }
    },
    
    // Hospital alerts - hospital users, drivers, and admins
    "hospitalAlerts": {
      ".read": "auth != null",
      "$hospitalId": {
        ".read": "auth != null && (root.child('users').child(auth.uid).child('hospitalId').val() == $hospitalId || root.child('users').child(auth.uid).child('role').val() == 'admin')",
        ".write": "auth != null && (root.child('users').child(auth.uid).child('hospitalId').val() == $hospitalId || root.child('users').child(auth.uid).child('role').val() == 'admin')"
      }
    },
    
    // Police alerts - police users, drivers, and admins
    "policeAlerts": {
      ".read": "auth != null",
      "$junctionId": {
        ".read": "auth != null && (root.child('users').child(auth.uid).child('assignedJunctionId').val() == $junctionId || root.child('users').child(auth.uid).child('role').val() == 'admin')",
        ".write": "auth != null && (root.child('users').child(auth.uid).child('assignedJunctionId').val() == $junctionId || root.child('users').child(auth.uid).child('role').val() == 'admin')"
      }
    },
    
    // RFID tags - admin only
    "rfidTags": {
      ".read": "root.child('users').child(auth.uid).child('role').val() == 'admin'",
      ".write": "root.child('users').child(auth.uid).child('role').val() == 'admin'"
    },
    
    // Drivers - admins can read/write
    "drivers": {
      ".read": "auth != null",
      "$driverId": {
        ".write": "root.child('users').child(auth.uid).child('role').val() == 'admin'"
      }
    }
  }
}
```

## Security Model

### Authentication Required
- All data requires authentication (`auth != null`)
- Anonymous access is not allowed
- Users must be logged in to access any data

### Role-Based Access Control

#### **Admin (admin_001)**
- **Full Access**: Can read and write all data
- Can manage users, ambulances, junctions, hospitals
- Can configure RFID tags and system settings

#### **Ambulance Driver (ambulance_driver)**
- **Own Ambulance**: Can read/write data for their assigned ambulance
- **Emergency Trips**: Can create and update their own trips
- **Junction Events**: Can read junction events (read-only)
- **Telemetry**: Can read and write LoRa telemetry
- **Hospitals**: Can read hospital information (read-only)

#### **Police (police)**
- **Assigned Junction**: Can read/write data for their assigned junction
- **Police Alerts**: Can read/write police alerts for their junction
- **Junction Events**: Can read/write junction events
- **Telemetry**: Can read/write LoRa telemetry
- **Hospitals**: Can read hospital information (read-only)

#### **Hospital (hospital)**
- **Own Hospital**: Can read/write alerts for their hospital
- **Hospital Alerts**: Can receive and manage incoming alerts
- **Emergency Trips**: Can read trips headed to their hospital
- **Telemetry**: Can read LoRa telemetry
- **Hospitals**: Can read hospital information (read-only)

## How to Apply These Rules

### Step 1: Go to Firebase Console
1. Open [Firebase Console](https://console.firebase.google.com/)
2. Select project: `smart-ambulance-36f9d`
3. Go to **Build** → **Realtime Database**

### Step 2: Update Rules
1. Click the **Rules** tab
2. Delete the existing test mode rules
3. Copy the production rules above
4. Paste them into the rules editor
5. Click **Publish**

### Step 3: Verify Rules
After publishing, Firebase will validate the rules. If there are syntax errors, you'll need to fix them before publishing.

## Testing the Rules

### Test 1: Unauthenticated Access
```bash
# Try to access database without authentication
# Should be denied
```

### Test 2: Role-Based Access
1. Login as driver_001
2. Try to read/write your ambulance data (AMB001) - Should succeed
3. Try to read/write another ambulance - Should fail
4. Try to modify users - Should fail

### Test 3: Admin Access
1. Login as admin_001
2. Try to read/write any data - Should succeed
3. Try to modify users - Should succeed

## Development vs Production Rules

### Development Rules (Test Mode)
```json
{
  "rules": {
    ".read": true,
    ".write": true
  }
}
```
- **Use During**: Development and testing
- **Risk**: Anyone with Firebase URL can access your data
- **Never Use**: In production

### Production Rules (Role-Based)
- **Use During**: Production deployment
- **Benefit**: Secure access control based on authentication and roles
- **Always Use**: In production

## Data Structure Validation

The rules assume this data structure:

```
users/
  {userId}/
    userId: string
    name: string
    pin: string
    role: string
    ambulanceId: string
    assignedJunctionId: string
    hospitalId: string
    active: boolean

ambulances/
  {ambulanceId}/
    ambulanceId: string
    driverId: string
    emergencyActive: boolean
    status: string
    destinationHospitalId: string
    severity: string
    lastLocation: object
    lastLoRaTelemetry: object

emergencyTrips/
  {tripId}/
    tripId: string
    ambulanceId: string
    driverId: string
    destinationHospitalId: string
    destinationHospitalName: string
    severity: string
    status: string
    startedAt: timestamp
    endedAt: timestamp

junctions/
  {junctionId}/
    junctionId: string
    name: string
    activeLane: string
    signalState: string
    preemptionMode: string
    approachThresholdMeters: number
    bearingToleranceDeg: number
    rssiFallbackThresholdDbm: number
    rssiConsecutivePacketCount: number
    gpsPacketTimeoutMs: number
    clearanceTimeoutMs: number

hospitalAlerts/
  {hospitalId}/
  {tripId}/
    tripId: string
    ambulanceId: string
    severity: string
    status: string
    eta: string
    message: string
```

## Common Issues and Solutions

### Issue: "Permission Denied" Errors
**Cause**: User doesn't have the required role or is not authenticated
**Solution**: 
- Ensure user is logged in
- Check user's role in Firebase Authentication
- Verify role matches the required access

### Issue: Admin Cannot Access Data
**Cause**: User's role is not set to 'admin' in Firebase
**Solution**:
- Check user data in Firebase Console → Authentication → Users
- Update user's role in Realtime Database → users/{userId}/role
- Ensure role is exactly "admin"

### Issue: Driver Cannot Access Their Ambulance
**Cause**: ambulanceId doesn't match or is not set
**Solution**:
- Check user's ambulanceId in users/{userId}/ambulanceId
- Ensure it matches the ambulance ID in ambulances/{ambulanceId}
- Data types must match (string vs number)

## Monitoring and Auditing

### Firebase Console Monitoring
1. Go to Firebase Console → Realtime Database
2. Check **Usage** tab for read/write statistics
3. Monitor for unusual access patterns
4. Review **Connections** for connected clients

### Audit Trail
- Firebase automatically logs all database operations
- Monitor Firebase Console → Realtime Database → Monitoring
- Set up alerts for suspicious activity

## Backup and Recovery

### Data Backup
- Firebase automatically backs up data
- Export data regularly using Firebase Console → Realtime Database → Export JSON
- Keep backups of critical configuration

### Recovery
- Use Firebase Console → Realtime Database → Import JSON
- Restore from automatic backups
- Re-apply security rules if needed

## Compliance Considerations

### HIPAA Compliance (If applicable)
- These rules provide basic access control
- For full HIPAA compliance, you may need:
  - Encryption at rest (Firebase provides this)
  - Encryption in transit (Firebase provides this)
  - Audit logging of all data access
  - Business associate agreements with hospitals

### GDPR Compliance (If applicable)
- These rules provide authentication-based access
- For full GDPR compliance, you may need:
  - Data deletion requests
  - Data export capabilities
  - Consent management
  - Data processing agreements

## Maintenance

### Regular Maintenance Tasks
1. Review access logs monthly
2. Audit user roles quarterly
3. Update rules when adding new features
4. Remove inactive users from Firebase Authentication
5. Monitor for unusual access patterns

### Rule Updates
When adding new features:
1. Review new data paths
2. Update security rules accordingly
3. Test new rules in development first
4. Gradually roll out to production
5. Monitor for any issues

## Emergency Access

### System Recovery Access
If you need to reset rules or gain emergency access:
1. Use Firebase Console → Realtime Database → Rules
2. Temporarily revert to test mode rules
2. Fix authentication issues
3. Re-apply production rules
4. Test thoroughly

### Service Account Access
For server-side access (e.g., dashboard):
1. Create Firebase service account
2. Use Firebase Admin SDK with service account credentials
3. Implement proper authentication in server code
4. Never expose service account keys in client-side code

## Summary

These production security rules provide:
- ✅ **Authentication Required**: No anonymous access
- ✅ **Role-Based Access**: Users can only access data relevant to their role
- ✅ **Admin Control**: Full access for system administration
- ✅ **Data Protection**: Prevents unauthorized access
- ✅ **Audit Trail**: Firebase logs all operations
- ✅ **Scalability**: Rules work as the system grows

**Important**: Always test rules in development before applying to production!