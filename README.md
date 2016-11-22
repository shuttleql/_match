# Game Service #

## Install sbt ##
```sh
brew install sbt
```

## Build & Run ##

```sh
$ sbt
> jetty:start
> browse
```

If `browse` doesn't launch your browser, manually open [http://localhost:8080/](http://localhost:8080/) in your browser.

## Other ##

### Set the appropriate environment variables

```sh
$ export DB_PG_URL="jdbc:postgresql://localhost:5432"
$ export DB_PG_USER=<username>
$ export DB_PG_PWD=<password>
$ export HMAC_SECRET=<secret>
$ export AMAZON_ACCESS_KEY=<access key>
$ export AMAZON_SECRET_KEY=<secret key>
$ export SNS_TOPIC_ARN="arn:aws:sns:us-west-2:796325253416:AdminNotifications"
```

### Set up initial db

```GET /setup```
