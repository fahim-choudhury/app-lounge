# App Lounge

App Lounge is an open-source application that allows you to install Android apps on your device quickly and easily. It is licensed and distributed under [The GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.en.html).

## Project Structure

App Lounge use the _Packaging by Features_ approach for packaging the code. A really good explanation for this approach can be found on Philip Hauer's [Package by Feature](https://web.archive.org/web/20211025104408/https://phauer.com/2020/package-by-feature/) blog post.

<details><summary>Click to expand</summary>

```
./root
├── api
│   ├── cleanapk
│   │   ├── blockedApps
│   │   └── data
│   │       ├── app
│   │       ├── categories
│   │       ├── download
│   │       ├── home
│   │       └── search
│   ├── database
│   ├── ecloud
│   │   └── modules
│   ├── exodus
│   │   ├── models
│   │   └── repositories
│   ├── fdroid
│   │   └── models
│   ├── fused
│   │   ├── data
│   │   └── utils
│   └── gplay
│       ├── token
│       └── utils
├── application
│   ├── model
│   └── subFrags
├── applicationlist
│   └── model
├── categories
│   └── model
├── di
├── home
│   └── model
├── manager
│   ├── database
│   │   └── fusedDownload
│   ├── download
│   │   └── data
│   ├── fused
│   ├── notification
│   ├── pkg
│   └── workmanager
├── purchase
├── receiver
├── search
├── settings
├── setup
│   ├── signin
│   │   └── google
│   └── tos
├── updates
│   └── manager
└── utils
    ├── enums
    └── modules

```

</details>

## API

App Lounge use the following APIs to offer applications:

- [GPlayApi](https://gitlab.com/AuroraOSS/gplayapi) from Aurora OSS
- [CleanAPK API](https://info.cleanapk.org/) from CleanAPK
- [Exodus API](https://github.com/Exodus-Privacy/exodus/blob/v1/doc/api.md) from Exodus-Privacy

## Development

- Documentation regarding development can be found on this repository's [wiki](https://gitlab.e.foundation/e/apps/apps/-/wikis/home)
- A the list of contributors can be viewed on this repository's [contributors graph](https://gitlab.e.foundation/e/apps/apps/-/graphs/master).

In case you wish to contribute to the development of this project, feel free to open a [Merge Request](https://gitlab.e.foundation/e/apps/apps/-/merge_requests) or an [Issue](https://gitlab.e.foundation/e/backlog/-/issues/) for the same. Contributions are always welcome.

## Murena's system app updates
The following section will give developer's input about how to trigger a release, and what to take care.

Most of the App lounge update implementation can be found in `/app/src/main/java/foundation/e/apps/data/gitlab` package.

The summarized process is:
1. Get OS's info (Android API, release type : beta, rc, etc.)
2. Define which Endpoint (json files from [this project](https://gitlab.e.foundation/e/os/system-apps-update-info) to contact to know which system app can be updated.
3. Download the json file. It list Updatable system app with package name & project ID and potentially other property for specific case.
    *By example, since BlissLauncher3 is updatable, we can use `"dependsOnAndroidVersion": true`. But we'll describe this further.*
4. Use [Gitlab's Release API](https://docs.gitlab.com/ee/api/releases/) to fetch & list available releases for each project.
5. Then get the latest available release. Download the json file available in the json's assets. This json files has been created with [this script](https://gitlab.e.foundation/e/os/system-apps-update-info/-/blob/main/scripts/create-json-files.sh?ref_type=heads)
6. It checks few element within the json file.
7. Compare version number with the installed one. If the update isn't more recent then stop.
8. Download APK from release thanks to URL defined in json files.
9. Once the APK is installed, it start installation process like for any regular app.

### How to make a new app updatable through app lounge ?
Let's call the new updatable app : "the new app" in the current section

If there is specific requirement for the new app, then App Lounge codebase (`/app/src/main/java/foundation/e/apps/data/gitlab`) must be updated accordingly.

In All case, the new app's project must have 5 CI-jobs. They rely on [those scripts](https://gitlab.e.foundation/e/os/system-apps-update-info/-/tree/main/scripts).

- `init-submodules`: which clones [the project](https://gitlab.e.foundation/e/os/system-apps-update-info) with needed scripts as a submodule 
- `generate-apks`: which signs the APK with all the key (Test, official & community) and provide them as artifact
- `create-json-files:`: which generate a json file with metadata about new app's release. This json files is used at step 5 in the summarized process described in previous section.
- `create-test-release` : Which is manually triggered, and that create a gitlab release for Test build only
- `create-release` : which is manually triggered and that create a gitlab release for Official and Community build

:warning: `create-test-release` and `create-release` will block each other because it use the name of the release's tag

<details><summary>Example from App Lounge's CI related to update</summary>

```yaml
variables:
  #  ... other variables
  UNSIGNED_APK: "AppLounge_release.apk"
  COMMUNITY_APK: "AppLounge_release_community.apk"
  OFFICIAL_APK: "AppLounge_release_official.apk"
  TEST_APK: "AppLounge_release_test.apk"

stages:
  # ... other stages
  - gitlab_release


init-submodules:
  stage: gitlab_release
  needs: []
  rules:
    - if: '$CI_COMMIT_TAG && $CI_COMMIT_REF_PROTECTED == "true"'
      when: on_success
  script:
    - git clone https://gitlab.e.foundation/e/os/system-apps-update-info.git systemAppsUpdateInfo
  artifacts:
    paths:
      - systemAppsUpdateInfo/scripts/

generate-apks:
  stage: gitlab_release
  rules:
    - if: '$CI_COMMIT_TAG && $CI_COMMIT_REF_PROTECTED == "true"'
      when: on_success
  needs:
    - init-submodules
    - buildRelease
    - buildReleaseTest
    - buildReleaseCommunity
    - buildReleaseOfficial
  dependencies:
    - init-submodules
    - buildRelease
    - buildReleaseTest
    - buildReleaseCommunity
    - buildReleaseOfficial
  script:
    - mkdir -p $APK_PATH
    - unsignedApk=$(ls app/build/outputs/apk/release/*.apk | grep "release")
    - testApk=$(ls app/build/outputs/apk/releaseTest/*.apk | grep "releaseTest")
    - communityApk=$(ls app/build/outputs/apk/releaseCommunity/*.apk | grep "releaseCommunity")
    - officialApk=$(ls app/build/outputs/apk/releaseOfficial/*.apk | grep "releaseOfficial")
    - cp "$unsignedApk" "$APK_PATH/$UNSIGNED_APK"
    - cp "$testApk" "$APK_PATH/$TEST_APK"
    - cp "$communityApk" "$APK_PATH/$COMMUNITY_APK"
    - cp "$officialApk" "$APK_PATH/$OFFICIAL_APK"
  artifacts:
    paths:
      - $APK_PATH/$UNSIGNED_APK
      - $APK_PATH/$TEST_APK
      - $APK_PATH/$COMMUNITY_APK
      - $APK_PATH/$OFFICIAL_APK

create-json-files:
  stage: gitlab_release
  dependencies:
    - init-submodules
    - generate-apks
  needs:
    - init-submodules
    - generate-apks
  rules:
    - if: '$CI_COMMIT_TAG && $CI_COMMIT_REF_PROTECTED == "true"'
      when: on_success
  script:
    - |
      ./systemAppsUpdateInfo/scripts/create-json-files.sh \
      "$APK_PATH" "$UNSIGNED_APK" "$COMMUNITY_APK" "$OFFICIAL_APK" "$TEST_APK"
  artifacts:
    paths:
      - test.json
      - community.json
      - official.json

create-test-release:
  stage: gitlab_release
  dependencies:
    - init-submodules
  needs:
    - init-submodules
    - create-json-files
    - generate-apks
  rules:
    - if: '$CI_COMMIT_TAG && $CI_COMMIT_REF_PROTECTED == "true"'
      when: manual
  script:
    - |
      ./systemAppsUpdateInfo/scripts/create-test-release.sh \
      "$APK_PATH" "$TEST_APK"
  allow_failure: true

create-release:
  stage: gitlab_release
  dependencies:
    - init-submodules
  needs:
    - init-submodules
    - create-json-files
    - generate-apks
  rules:
    - if: '$CI_COMMIT_TAG && $CI_COMMIT_REF_PROTECTED == "true"'
      when: manual
  script:
    - |
      ./systemAppsUpdateInfo/scripts/create-release.sh \
      "$APK_PATH" "$UNSIGNED_APK" "$COMMUNITY_APK" "$OFFICIAL_APK"
  allow_failure: true
```

</details>


Then you need to add some CI variables to the new app's project (Settings > CI-CD > Variables). They are mainly the signature key & alias to sign the APK. 
Check already updatable project to just copy/paste those variables. 

You also need to define some rules to make make some tag protected. Because Only a protected tag can trigger a release (Settings > repository > protected tags)

Tag example: `v*` or `eOS-release-*`

Thanks to that, when you will create a tag like `v14.55.8` it will be protected and the pipeline will run automatically the release step until a manual action is required

In the end, you will need to add the ID of new app's project, package name & any other property you want in [this json file](https://gitlab.e.foundation/e/os/system-apps-update-info/-/blob/main/updatable_system_apps_test.json?ref_type=heads) to make it visible by no-releasable build (beta, rc & test). And if you want to release for User, just do the same in [this json file](https://gitlab.e.foundation/e/os/system-apps-update-info/-/blob/main/updatable_system_apps.json?ref_type=heads)


**note:** `dependsOnAndroidVersion": true` this option is used only in BlissLaunche 3 at the moment, to let App lounge know that this project has build depending on Android version; So with specific branch (v1-t, v1-s, etc.). Thanks to it, App lounge can filter release for the Android version of the OS which prevent installation of the update for android T on OS with android S by example.