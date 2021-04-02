lazy val datastore = project
  .in(file("datastore"))
  .settings(Release.publishSettings)

lazy val cli = project
  .in(file("datastore-cli"))
  .settings(Release.publishSettings)
  .dependsOn(datastore)

lazy val root = project
  .in(file("."))
  .aggregate(datastore, cli)
  .settings(Release.noPublishSettings)
