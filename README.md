# Chabonze

Chabonze is a slack bot that watches tweets on the twitter list and post them to the slack channel.

## Setup

### Configure Slack settings

#### Register bot

Register a bot with your slack team. 

https://my.slack.com/services/new/bot

`API Token` will be needed　to deploy chabonze later.

#### Enable Twitter integration

Twitter Integration provides the feature that expand pasted Twitter URLs, displaying the full tweet and attachd media.
So you need to enable it.

https://my.slack.com/apps

* Search 'Twitter'
* Just add Twitter Integartion

### Configure Twitter settings

Register application with your twitter account.

https://apps.twitter.com/app/new

`Consumer key` and `Consumer secret` will be needed　to deploy chabonze later.

## Deploy

### Build 

Leiningen is required to build executable jar.

```
git clone https://github.com/nijohando/chabonze.git
cd chabonze
lein uberjar
```

### Run

chabonze requires environment variables below.

```
export CHABONZE_SLACK_API_TOKEN=<Slack API Token>
export CHABONZE_STORE_PATH=/path/to/store.edn
export CHABONZE_TWITTER_CONSUMER_KEY=<Twitter Consumer Key>
export CHABONZE_TWITTER_CONSUMER_SECRET=<Twitter Cosumer Secret>
```

`CHABONZE_STORE_PATH` is defined as a file path for storing persistent data of chabonze.
These data also include Twitter access token. Please pay attention to file permissions.

```
java -jar target/chabonze-standalone.jar
```

## Chat with chabonze

Chabonze　recognizes mentioned message beginning with a slash as commands.

```
@<botname> /<command> <args>
```

Currently only twitter command is implemented. 

```
@<botname> /twitter help
```

And it has several subcommands.

```
Usage: /twitter <command> [<args>]

COMMAND   DESCRIPTION
-------------------------------------------------
auth      Connect to a twitter account
lists     Show lists
watch     List, create, or delete watcher task
help      Show help
```

### Authroize chabonze as twitter application

You need to connect chabonze to your twitter account using `auth` subcommand.

```
@<botname> /twitter auth -h
```

```
Usage: /twitter auth -r
   or: /twitter auth -p <PINCODE>

Options:
  -r, --request          request for issuing new pincode
  -p, --pincode PINCODE  authorize with pincode
  -h, --help
```

`auth` subcommand with `-r` option starts pincode based OAuth authorization.

```
@<botname> /twitter auth -r
```

Chabonze returns a URL where pincode is displayed, You need to access the url in the browser to authoroze him.

```
https://api.twitter.com/oauth/authorize?oauth_token=xxxxxxxxx
```

After you obtain picode, tell it to chabonze with `auth` subcommand with `-p` option.

```
@<botname> /twitter auth -p <pincode>
```

### Show list

`lists` subcommand just show twitter lists.

```
@<botname> /twitter lists
```

```
SLUG        MODE      MEMBERS   NAME
------------------------------------------
clojure     private   21        clojure
i           private   3         i
study       private   10        study
work        private   33        work
tech        private   34        tech
engineers   private   42        engineers
```

### Watch list timeline

You can subscribe and unsubscribe twitter list timeline on a slack channel using `watch` subcommand.

```
@<botname> /twitter watch -h
```

```
Usage: /twitter watch -l
   or: /twitter watch -a <SLUG> -i <INTERVAL>
   or: /twitter watch -d <TASK-ID>

Options:
  -l, --list                  show watch tasks
  -a, --add SLUG              add watch task
  -i, --interval MINUTES  10  watch interval
  -d, --delete TASK-ID        delete watch task
  -h, --help
```

Invite chabonze to the channel you want to subscribe to the list timeline.
And then request chabonze using `watch` subcommand with `-a` and `-i` option.

```
@<botname> /twitter watch -a tech -i 15
```
