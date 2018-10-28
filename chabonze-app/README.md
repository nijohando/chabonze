# chabonze-app

chabonze-app is a duct beased slack bot application.  

## Setup

### Create a Bot configuration

Create a bot configuration on your slack team. 

https://my.slack.com/services/new/bot

`API Token` will be needed to deploy chabonze later.

### Attach modules

Chabonze is designed to be extensible its functionality by duct modules.  
So it necesary to attach a module to add the function to the bot.

For details, refer to the module's doc.  

## Deploy

### Build

Leiningen is required to build executable jar.

```
git clone https://github.com/nijohando/chabonze.git
cd chabonze/chabonze-app
lein uberjar
```

### Run

chabonze requires environment variables below.

```
export SLACK_API_TOKEN=<Slack API Token>
export STORE_PATH=/path/to/store.edn
export TWITTER_OAUTH_CONSUMER_KEY=<Twitter Consumer Key>
export TWITTER_OAUTH_CONSUMER_SECRET=<Twitter Cosumer Secret>
```

`STORE_PATH` is defined as a file path for storing persistent data of chabonze.
These data also include Twitter access token. Please pay attention to file permissions.

```
java -jar target/chabonze.jar
```


## Chat with chabonze

Chabonze recognizes mentioned message beginning with a slash as commands.

```
@<botname> /<command> <args>
```

`/help` command shows available commands.

```
@<botname> /help
```
