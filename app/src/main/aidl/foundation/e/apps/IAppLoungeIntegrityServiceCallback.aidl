package foundation.e.apps;

interface IAppLoungeIntegrityServiceCallback {
    void onSuccess(String token);
    void onError(int errorCode);
}