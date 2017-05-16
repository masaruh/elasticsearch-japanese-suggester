Completion suggester for Japanese for Elasticsearch
==================================

This plugin includes:
* "kuromoji_suggest" tokenizer, extension for Lucene's Japanese (kuromoji) Analyzer to enable query completion.
* "japanese_completion" suggester, customized completion suggester for Japanese.

You need to install a version matching your Elasticsearch version:

| elasticsearch |  Kuromoji Analysis Suggest Plugin |
|---------------|-----------------------------|
| es-2.2.0        |     0.22.0         |
| es-2.2.1        |     0.22.1         |
| es-2.2.2        |     0.22.2         |
| es-5.0.0        |     5.0.0          |
| es-5.1.1        |     5.1.1          |
| es-5.2.0        |     5.2.0          |
| es-5.3.0        |     5.3.0          |
| es-5.4.0        |     5.4.0          |


## Prerequisites
* (Optional) To deal with full-width and half-width characters, elasticsearch-analysis-icu(https://github.com/elasticsearch/elasticsearch-analysis-icu)

## Installation
In order to install the plugin, run:

```sh
# In elasticsearch home directory
bin/plugin install https://github.com/masaruh/elasticsearch-japanese-suggester/releases/download/0.20.0/elasticsearch-japanese-suggester-0.20.0.zip
```

## Exampl Usage

### Create index (5.0.0 or above)
```
PUT suggest_sample
{
  "mappings": {
    "test": {
      "properties": {
        "suggest": {
          "type": "completion",
          "analyzer": "kuromoji_suggest_index",
          "search_analyzer": "kuromoji_suggest_search"
        }
      }
    }
  }
}
```

### Create index (0.22.2 or below)
```sh
curl -XPUT "http://localhost:9200/suggest_sample" -d'
{
  "mappings": {
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
}'
```

### Index a document
```
PUT /suggest_sample/test/1
{
  "suggest":"東京駅"
}
```

### Search
```
GET /suggest_sample/_suggest
{
    "suggest" : {
        "text" : "とうk",
        "japanese_completion" : {
            "field" : "suggest"
        }
    }
}
'

{
  "_shards": {
    "total": 5,
    "successful": 5,
    "failed": 0
  },
  "suggest": [
    {
      "text": "とうk",
      "offset": 0,
      "length": 3,
      "options": [
        {
          "text": "東京駅",
          "_index": "suggest_sample",
          "_type": "test",
          "_id": "1",
          "_score": 1,
          "_source": {
            "suggest": "東京駅"
          }
        }
      ]
    }
  ]
}
```



License
-------
See LICENSE.txt