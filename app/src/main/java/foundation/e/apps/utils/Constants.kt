/*
    Copyright (C) 2019  e Foundation

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package foundation.e.apps.utils

object Constants {

    // Global
    const val BASE_URL = "https://api.cleanapk.org/v2/"
    const val DOWNLOAD_URL = "https://apk.cleanapk.org/"
    const val STORAGE_PERMISSION_REQUEST_CODE = 0
    const val CONNECT_TIMEOUT = 30000 // 30 seconds
    const val READ_TIMEOUT = 60000 // 60 seconds
    const val REQUEST_METHOD_GET = "GET"
    const val REQUEST_METHOD_POST = "POST"

    // Search
    const val MIN_SEARCH_TERM_LENGTH = 3;
    const val RESULTS_PER_PAGE = 20
    const val SUGGESTION_KEY = "suggestion"
    const val SUGGESTIONS_RESULTS = 5

    // Application
    const val WEB_STORE_URL = "https://cleanapk.org/#/app/"
    const val APPLICATION_PACKAGE_NAME_KEY = "application_package_name"
    const val APPLICATION_DESCRIPTION_KEY = "application_description"
    const val SELECTED_APPLICATION_SCREENSHOT_KEY = "selected_application_screenshot"

    // Categories
    const val CATEGORY_KEY = "category_key"

    // Home
    const val CURRENTLY_SELECTED_FRAGMENT_KEY = "currently_selected_fragment"

    // Updates
    const val OUTDATED_APPLICATIONS_FILENAME = "outdated_applications.txt"
    const val UPDATES_WORK_NAME = "updates_work"
    const val UPDATES_NOTIFICATION_ID = 76
    const val UPDATES_NOTIFICATION_CHANNEL_ID = "updates_notification"
    const val UPDATES_NOTIFICATION_CHANNEL_TITLE = "App updates"
    const val UPDATES_NOTIFICATION_CLICK_EXTRA = "updates_notification_click_extra"

}
