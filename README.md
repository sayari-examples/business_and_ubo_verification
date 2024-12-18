# Sayari Business Verification and UBO Cli


## Intro

This is a cli which serves as an example of Sayari's suggested methodology for perfroming business verification and UBO calculation using the Sayari API.  The cli performs the following steps.

1. Authenticate to the Sayari API using oauth2.
2. Match the entity described by the provided command line args against the Sayari dataset.
3. For each macthed entity, get the entity profile which includes the risks associated with that entity
4. For each matched entity, traverse the graph to find all the UBO entities and their related risks.

## Python

### Install
```
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

### Run
```
export SAYARI_CLIENT_ID=abc
export SAYARI_CLIENT_SECRET=zyx
python business_and_ubo_verification.py -n "协和干细胞基因工程有限公司山西分公司" -a "太原高新区佳华街7号山西帅科大厦二层213房"
```

## Java

### Install
```
mvn clean package
```

### Run
```
export SAYARI_CLIENT_ID=abc
export SAYARI_CLIENT_SECRET=zyx
java -cp target/citi-example-1.0.0-jar-with-dependencies.jar com.sayari.BusinessAndUboVerification --name "CH2M HILL CONSTRUCTORS, INC." --country "GBR"
```




