# App Lounge

App Lounge is an open-source application that allows you to install Android apps on your device quickly and easily. It is licensed and distributed under [The GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.en.html).

## Project Structure

App Lounge use the _Packaging by Features_ approach for packaging the code. A really good explanation for this approach can be found on Philip Hauer's [Package by Feature](https://web.archive.org/web/20211025104408/https://phauer.com/2020/package-by-feature/) blog post.

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

## API

App Lounge use the following APIs to offer applications:

- [GPlayApi](https://gitlab.com/AuroraOSS/gplayapi) from Aurora OSS
- [CleanAPK API](https://info.cleanapk.org/) from CleanAPK
- [Exodus API](https://github.com/Exodus-Privacy/exodus/blob/v1/doc/api.md) from Exodus-Privacy

## Development

- Documentation regarding development can be found on this repository's [wiki](https://gitlab.e.foundation/e/apps/apps/-/wikis/home)
- A the list of contributors can be viewed on this repository's [contributors graph](https://gitlab.e.foundation/e/apps/apps/-/graphs/master).

In case you wish to contribute to the development of this project, feel free to open a [Merge Request](https://gitlab.e.foundation/e/apps/apps/-/merge_requests) or an [Issue](https://gitlab.e.foundation/e/backlog/-/issues/) for the same. Contributions are always welcome.
