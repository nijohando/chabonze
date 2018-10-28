# module.bot

[![Clojars Project](https://img.shields.io/clojars/v/jp.nijohando.chabonze/module.bot.svg)](https://clojars.org/jp.nijohando.chabonze/module.bot)

module.bot is a core module for chabonze.


## RTM API Client 

`:jp.nijohando.chabonze.bot.slack/rtm` is [Real Time Messaging API](https://api.slack.com/rtm) client component.  
The component connects to slack via websocket and acts as [nijohando/event](https://github.com/nijohando/event) bus.

### Events

| Path        | Value                                                                     | Trigger                                                          |
| :-          | :-                                                                        | :-                                                               |
| /connect    | nil                                                                       | [hello message](https://api.slack.com/events/hello) is received. |
| /disconnect | nil                                                                       | websocket is disconnected.                                       |
| /event      | [RTM Events](https://api.slack.com/rtm) (JSON)                            | RTM Event is received.                                           |
| /reply/:id  | [RTM Responses / Errors](https://api.slack.com/rtm) (JSON)                | RTM Response / Error is received.                                |
| /error      | [nijohando/failable](https://github.com/nijohando/failable) failure value | Unexpected error is occurred.                                    |

### Functions

#### send-message

`(send-message client channel-id text)`

Sends a text as [message event](https://api.slack.com/events/message).  
`client` is a client component.  
`channel-id` is ID of the slack channel.  
`text` is string to send.


#### send-typing

`(send-typing client channel-id)`

Sends a typing indicator.

`client` is a client component.  
`channel-id` is ID of the slack channel. 


## Web API Client

`:jp.nijohando.chabonze.bot.slack/web` is [Slack Web API](https://api.slack.com/web) client component.  

### Functions

#### post-message

`(post-message client msg)`

Sends a message to a channel via [chat.postMessage API](https://api.slack.com/methods/chat.postMessage).

`client` is a client component.  
`msg` is a post parameters for `chat.postMessage`.


#### channels

`(channels client)`

Gets lists all channels in a Slack team via [channels.list API](https://api.slack.com/methods/channels.list).
Returns a map whose key is channel-id and value is map of limited [channel type](https://api.slack.com/types/channel).

`client` is a client component.


#### channel

`(channel client)`

Gets information about a channel via [channels.info API](https://api.slack.com/methods/channels.info).
Returns a map of [channel type](https://api.slack.com/types/channel).



## Store

`:jp.nijohando.chabonze.bot/store` reads from and writes to store data.  
Store data can be referred to by [deref](https://clojuredocs.org/clojure.core/deref)

### Functions

#### transact!

`(transact! store f & args)`

Updates store data in transaction.  
Returns the agent that writes to the store file.
