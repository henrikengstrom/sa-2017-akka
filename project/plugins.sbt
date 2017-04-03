credentials += Credentials(Path.userHome / ".lightbend" / "commercial.credentials")

resolvers += Resolver.url("lightbend-commercial", url("https://repo.lightbend.com/commercial-releases"))(Resolver.ivyStylePatterns)

addSbtPlugin("com.lightbend.cinnamon" % "sbt-cinnamon" % "2.3.0")
