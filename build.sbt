import sbt.Keys.libraryDependencies

val project = Project(id = "saconf", base = file(".")) //enablePlugins (Cinnamon)

name := """saconf"""

version := "1.0"

scalaVersion := "2.11.8"

lazy val akkaVersion = "2.4.17"
lazy val akkaHttpVersion = "10.0.5"
lazy val scalaTestVersion = "3.0.0"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream"           % akkaVersion,
  
  "com.typesafe.akka" %% "akka-cluster"          % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools"    % akkaVersion,
  
  "com.typesafe.akka"        %% "akka-persistence" % akkaVersion,
  "org.iq80.leveldb"          % "leveldb"          % "0.7",
  "org.fusesource.leveldbjni" % "leveldbjni-all"   % "1.8",
  
  "com.typesafe.akka" %% "akka-http"            % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-testkit"    % akkaHttpVersion,
  
  // Alpakka
  "com.typesafe.akka" %% "akka-stream-kafka" % "0.14",
  
  "org.scalatest"     %% "scalatest"    % scalaTestVersion % "test",
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion      % "test"

  // Adds monitoring capabilities to the project
  //,
  //Cinnamon.library.cinnamonSandbox
)

//cinnamon in run := true
//cinnamon in test := true
connectInput in run := true
