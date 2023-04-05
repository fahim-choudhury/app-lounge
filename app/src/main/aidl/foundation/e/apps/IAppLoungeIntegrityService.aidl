package foundation.e.apps;

import foundation.e.apps.IAppLoungeIntegrityServiceCallback;

interface IAppLoungeIntegrityService {
    void checkIntegrity(String packageName, String nonce, IAppLoungeIntegrityServiceCallback callback);
}