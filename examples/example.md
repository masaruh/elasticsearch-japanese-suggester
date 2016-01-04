### Turn on CORS
Add below to config/elasticsearch.yml
```
http.cors.enabled: true
```

### Create Index
```sh
curl -XPUT 'http://localhost:9200/suggest_sample/' -d'
{
  "index": {
    "analysis": {
      "analyzer": {
        "kuromoji_suggest_index":{
          "tokenizer":"kuromoji_suggest_index"
        },
        "kuromoji_suggest_search":{
          "tokenizer":"kuromoji_suggest_search"
        }
      },

      "tokenizer":{
        "kuromoji_suggest_index":{
          "type": "kuromoji_suggest",
          "expand":true
        },
        "kuromoji_suggest_search":{
          "type": "kuromoji_suggest",
          "expand":false
        }
      }
    }
  }
}
'
```

### Put mapping
Then add completion suggester configuration:
```sh
curl -XPUT 'http://localhost:9200/suggest_sample/_mapping' -d'
{
  "test": {
    "properties": {
      "suggest": {
        "type": "japanese_completion",
        "analyzer": "kuromoji_suggest_index",
        "search_analyzer": "kuromoji_suggest_search",
        "payloads": true
      }
    }
  }
}
'
```

### Feed document
```sh
curl -XPUT 'http://localhost:9200/suggest_sample/test/1' -d'
{
  "suggest":"東京駅"
}
```

### Open index.html and type "t" or "と"..etc
