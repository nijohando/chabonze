# module.twitter

[![Clojars Project](https://img.shields.io/clojars/v/jp.nijohando.chabonze/module.twitter.svg)](https://clojars.org/jp.nijohando.chabonze/module.twitter)

module.twitter provides function to subscribe twitter lists and search result on slack.

## Setup

5 steps to install module.twitter to chabonze-app.

1. Enable twitter integration (Slack)
2. Register application (Twitter)
3. Add module dependency (chabonze-app)
4. Add module configuration (chabonze-app)
5. Add environment variables (chabonze-app)

### 1. Enable twitter integration

Twitter integration must be enabled in your slack team.  
This integration provides the feature that expand posted Twitter URLs, displaying the full tweet and attachd media.

1. Open the url https://my.slack.com/apps 
2. Search `twitter`
3. Just add Twitter integration

It's not necessary to configure any parameters.

### 2 Register application

App must be registered with your twitter account at https://apps.twitter.com/app/new  
You will get a oauth consumer key and its secret.

### 3. Add module dependency

Add module dependency to project.clj in chabonze-app

```clojure
[jp.nijohando.chabonze/module.twitter "0.1.2"]
```

### 4. Add module configuration

Add module configuration to config.edn in chabonze-app


```clojure
{:duct.profile/base
 ...
 :jp.nijohando.chabonze.module/twitter {}
 ... } 
```

### 5. Add environment variables

Add oauth consumer key and its secret acquried in step 2 to environment variables.

```
export TWITTER_OAUTH_CONSUMER_KEY=<CONSUMER_KEY>
export TWITTER_OAUTH_CONSUMER_SECRET=<CONSUMER_SECRET>
```

## Usage

After installing module.twitter, Chabonze recognizes mentioned message beginning with a `/twitter` as twitter command.

```
@<botname> /twitter
```

`/twitter` command without arguments shows usage.

```
Usage: /twitter <command> [<args>]

COMMAND   DESCRIPTION                                 
---------------------------------------------------------
auth      Authorize the bot to access Twitter.        
list      Show twitter lists.                         
watch     Watch lists or search result on the channel.
```

### auth

Before using `list`, `watch`, OAuth authentication must be performed by `auth` subcommand.

```
Usage: /twitter auth -r
   or: /twitter auth -p <PINCODE>

Options:
  -r, --request          request for issuing new pincode
  -p, --pincode PINCODE  authorize with pincode
  -h, --help
```

`auth -r` starts PIN based OAuth and shows the url to get a pincode.

```
https://api.twitter.com/oauth/authorize?oauth_token=<token>
```


After getting a pincode,

`auth -p <PINCODE>` gets an access token to access twitter.

### list

`list` subcommand shows all lists in your twitter account.

```
SLUG      MODE      MEMBERS   NAME   
----------------------------------------
weather   private   13        weather
cooking   private   4         cooking
traffic   private   15        traffic
```

### watch

`watch` subcommand creates, deletes and lists watch tasks.

```
Usage: /twitter watch -l
   or: /twitter watch -a <SLUG> -i <INTERVAL>
   or: /twitter watch -A <QUERY> -i <INTERVAL>
   or: /twitter watch -t -i <INTERVAL>
   or: /twitter watch -d <TASK-ID>

Options:
  -l, --list                   show watch tasks
  -a, --add-list SLUG          add list watch task
  -A, --add-query QUERY        add query watch task
  -t, --add-home-timeline      add home timeline watch task
  -i, --interval MINUTES   10  watch interval
  -d, --delete TASK-ID         delete watch task
  -h, --help
```

`watch -a <SLUG>` Creates new watch task on current channel to subscribe to the twitter list.

`watch -A <QUERY>` Same as `watch -a`, but subscribes to the search results and the query can include the operators listed in [Search Tweets](https://developer.twitter.com/en/docs/tweets/search/guides/standard-operators.html)

Also lang parameter can be specified in a query as follows.  
```
watch -A "clojure lang:ja"
```

`watch -t` Creates new watch task to subscribe the home timeline.

`watch -l` Show list of watch tasks.

```
TASK-ID   CHANNEL         TYPE    TARGET                               INTERVAL(min)
---------------------------------------------------------------------------------------
1         tw-traffic      list    traffic                              10           
2         tw-clojure-ja   query   {:strings ["clojure"], :lang "ja"}   10   
```

`watch -d <TASK-ID>` deletes a watch task.
