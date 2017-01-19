## druid4s

[![Build Status](https://travis-ci.org/GlobalWebIndex/druid4s.svg?branch=master)](https://travis-ci.org/GlobalWebIndex/druid4s)

```
"net.globalwebindex" %% "druid4s" % "0.1.2-SNAPSHOT"
```

Druid is mostly accessed by 4 means : 

1. Querying Broker by client, usually javascript/browser based analytics/dashboard applications
2. Real-time indexing is pull based oriented (from a queue like kafka)
3. Bulk indexing - consists in submitting an indexing task with definition of the indexing process
4. Querying Coordinator for metadata

This is a druid client library that allows for `1)`, `3)` and `4)`.

**It is a WIP and I don't recommend using it until I replenish specs that I deleted because at time of writing I didn't know about some druid tooling I can use to test it properly**