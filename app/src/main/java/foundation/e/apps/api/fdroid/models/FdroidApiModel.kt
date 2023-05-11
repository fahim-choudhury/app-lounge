package foundation.e.apps.api.fdroid.models

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Data class for requests to F-droid API.
 * https://gitlab.com/fdroid/fdroiddata/-/raw/master/metadata/<package_name>.yml
 *
 * An empty constructor is required to allow parsing by Jackson.
 * https://facingissuesonit.com/2019/07/17/com-fasterxml-jackson-databind-exc-invaliddefinitionexception-cannot-construct-instance-of-xyz-no-creators-like-default-construct-exist-cannot-deserialize-from-object-value-no-delega/
 *
 * Jackson annotations can be found here:
 * https://github.com/FasterXML/jackson-annotations
 *
 * Currently only being used to fetch author name.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class FdroidApiModel() {
    var authorName: String = ""
    var builds: List<BuildInfo> = mutableListOf()

    @JsonCreator
    constructor(
        @JsonProperty("AuthorName") AuthorName: String?,
        @JsonProperty("Builds") Builds: List<BuildInfo>?,
    ) : this() {
        this.authorName = AuthorName ?: ""
        this.builds = Builds ?: emptyList()
    }
}
