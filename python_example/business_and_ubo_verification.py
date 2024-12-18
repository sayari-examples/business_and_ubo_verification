import requests
import os
import sys
import urllib.parse
import argparse


def get_auth_token():
    data = {
      "client_id": os.getenv("SAYARI_CLIENT_ID"),
      "client_secret": os.getenv("SAYARI_CLIENT_SECRET"),
      "audience":"sayari.com",
      "grant_type":"client_credentials"
    }
    r = requests.post("https://api.sayari.com/oauth/token", headers={"content-type": "application/json"}, json=data)
    return r.json()['access_token']


def resolve_entity(s, token, query_string):
    r = s.get(f"https://api.sayari.com/v1/resolution?{query_string}", headers={"content-type": "application/json", "Authorization": "Bearer " + token})
    return r.json()


def get_entity(s, token, sayari_id):
    r = s.get(f"https://api.sayari.com/v1/entity/{sayari_id}", headers={"content-type": "application/json", "Authorization": "Bearer " + token})
    return r.json()


def get_ubo(s, token, sayari_id, offset=0):
    r = s.get(f"https://api.sayari.com/v1/ubo/{sayari_id}?limit=50&offset={offset}", headers={"content-type": "application/json", "Authorization": "Bearer " + token})
    return r.json()


def assemble_query_string(name=None, identifier=None, country=None, address=None, date_of_birth=None, contact=None, entity_type='vessel'):
    query_string = []
    if name:
        url_name = urllib.parse.quote_plus(name)
        query_string.append(f"name={url_name}")
    if identifier:
        url_identifier = urllib.parse.quote_plus(identifier)
        query_string.append(f"identifier={url_identifier}")
    if country:
        url_country = urllib.parse.quote_plus(country)
        query_string.append(f"country={url_country}")
    if address:
        url_address = urllib.parse.quote_plus(address)
        query_string.append(f"address={url_address}")
    if date_of_birth:
        url_date_of_birth = urllib.parse.quote_plus(date_of_birth)
        query_string.append(f"date_of_birth={url_date_of_birth}")
    if contact:
        url_contact = urllib.parse.quote_plus(contact)
        query_string.append(f"contact={url_contact}")
    if entity_type:
        url_entity_type = urllib.parse.quote_plus(entity_type)
        query_string.append(f"entity_type={url_entity_type}")
    return "&".join(query_string)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(
                    prog='business_and_ubo_verification.py',
                    description='This is an example of how to perform Business Verification and UBO calculation using the Sayari API',
                    epilog='For further assistance, please contact sayari')
    parser.add_argument('-n', '--name')
    parser.add_argument('-i', '--identifier')
    parser.add_argument('-c', '--country')
    parser.add_argument('-a', '--address')
    parser.add_argument('-d', '--dob')
    parser.add_argument('-o', '--contact')
    parser.add_argument('-t', '--type')
    args = parser.parse_args()
    if not ((args.name and args.address) or (args.name and args.country) or (args.name and args.identifier) or (args.name)):
        sys.exit("Not enough information provided to match the entity")


    s = requests.Session()
    tok = get_auth_token()
    query = assemble_query_string(args.name, args.identifier, args.country, args.address, args.dob, args.contact, args.type)

    resolved = resolve_entity(s, tok, query)
    for r in resolved['data']:
        print(f"Sayari matched {r['label']} with an id of {r['entity_id']} at a strength of {r['match_strength']['value']}\n")

        e = get_entity(s, tok, r['entity_id'])
        if 'latest_status' in e:
            print(f"Sayari identifed risks of {list(e['risk'].keys())}. {e['label']} latest status is {e['latest_status']['status']}.\n")
        else:
            print(f"Sayari identifed risks of {list(e['risk'].keys())}. {e['label']} latest status is unknown.\n")

        identified_ubo = []
        u = get_ubo(s, tok, e['id'])
        for i in u['data']:
            identified_ubo.append({f"{i['target']['id']}": {"name": f"{i['target']['label']}", "risks": list(i['target']['risk'].keys())}})
        offset = 0
        if 'next' in u and u['next'] == True and offset <= 9950:
            offset = offset + 50
            u = get_ubo(s, tok, e['id'], offset)
            for i in u['data']:
                identified_ubo.append({f"{i['target']['id']}": {"name": f"{i['target']['label']}", "risks": list(i['target']['risk'].keys())}})
        print(f"The Sayari identified UBO for and their risks for {r['label']} with an id of {r['entity_id']} is {identified_ubo}\n")
