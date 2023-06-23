package foundation.e.apps;

import foundation.e.apps.IAppLoungeIntegrityServiceCallback;

interface IAppLoungeIntegrityService {
    void checkIntegrity(String packageName, String nonce, String droidGuardToken, IAppLoungeIntegrityServiceCallback callback);
}