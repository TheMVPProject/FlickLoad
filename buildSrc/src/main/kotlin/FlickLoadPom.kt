/**
 * Shared POM configuration for FlickLoad modules.
 *
 * Call configurePom(project) inside a mavenPublishing {} block
 * to set up all POM metadata from gradle.properties.
 */

import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPom

fun MavenPom.configureFlickLoadPom(project: Project) {
    url.set(project.property("POM_URL").toString())

    licenses {
        license {
            name.set(project.property("POM_LICENCE_NAME").toString())
            url.set(project.property("POM_LICENCE_URL").toString())
            distribution.set(project.property("POM_LICENCE_DIST").toString())
        }
    }

    developers {
        developer {
            id.set(project.property("POM_DEVELOPER_ID").toString())
            name.set(project.property("POM_DEVELOPER_NAME").toString())
        }
    }

    scm {
        url.set(project.property("POM_SCM_URL").toString())
        connection.set(project.property("POM_SCM_CONNECTION").toString())
        developerConnection.set(project.property("POM_SCM_DEV_CONNECTION").toString())
    }
}
