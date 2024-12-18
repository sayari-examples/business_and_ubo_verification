package com.sayari;

import org.apache.commons.cli.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class BusinessAndUboVerification {

    private static final String TOKEN_URL = "https://api.sayari.com/oauth/token";
    private static final String RESOLUTION_URL = "https://api.sayari.com/v1/resolution";
    private static final String ENTITY_URL = "https://api.sayari.com/v1/entity";
    private static final String UBO_URL = "https://api.sayari.com/v1/ubo";

    private static HttpClient client = HttpClient.newBuilder().build();

    private static String getEnv(String name) {
        String val = System.getenv(name);
        if (val == null || val.isEmpty()) {
            throw new RuntimeException("Environment variable " + name + " is not set or empty.");
        }
        return val;
    }

    private static String getAuthToken() throws IOException, InterruptedException {
        JSONObject data = new JSONObject();
        data.put("client_id", getEnv("SAYARI_CLIENT_ID"));
        data.put("client_secret", getEnv("SAYARI_CLIENT_SECRET"));
        data.put("audience", "sayari.com");
        data.put("grant_type", "client_credentials");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(data.toString()))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JSONObject respJson = new JSONObject(response.body());
        return respJson.getString("access_token");
    }

    private static JSONObject resolveEntity(String token, String queryString) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(RESOLUTION_URL + "?" + queryString))
                .header("content-type", "application/json")
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return new JSONObject(response.body());
    }

    private static JSONObject getEntity(String token, String sayariId) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENTITY_URL + "/" + sayariId))
                .header("content-type", "application/json")
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return new JSONObject(response.body());
    }

    private static JSONObject getUbo(String token, String sayariId, int offset) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(UBO_URL + "/" + sayariId + "?limit=50&offset=" + offset))
                .header("content-type", "application/json")
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return new JSONObject(response.body());
    }

    private static String assembleQueryString(String name, String identifier, String country, String address, String dateOfBirth, String contact, String entityType) {
        List<String> params = new ArrayList<>();
        if (name != null && !name.isEmpty()) {
            params.add("name=" + urlEncode(name));
        }
        if (identifier != null && !identifier.isEmpty()) {
            params.add("identifier=" + urlEncode(identifier));
        }
        if (country != null && !country.isEmpty()) {
            params.add("country=" + urlEncode(country));
        }
        if (address != null && !address.isEmpty()) {
            params.add("address=" + urlEncode(address));
        }
        if (dateOfBirth != null && !dateOfBirth.isEmpty()) {
            params.add("date_of_birth=" + urlEncode(dateOfBirth));
        }
        if (contact != null && !contact.isEmpty()) {
            params.add("contact=" + urlEncode(contact));
        }
        if (entityType != null && !entityType.isEmpty()) {
            params.add("entity_type=" + urlEncode(entityType));
        }
        return String.join("&", params);
    }

    private static String urlEncode(String val) {
        return URLEncoder.encode(val, StandardCharsets.UTF_8);
    }

    public static void main(String[] args) {
        Options options = new Options();

        options.addOption("n", "name", true, "Name");
        options.addOption("i", "identifier", true, "Identifier");
        options.addOption("c", "country", true, "Country");
        options.addOption("a", "address", true, "Address");
        options.addOption("d", "dob", true, "Date of Birth");
        options.addOption("o", "contact", true, "Contact");
        options.addOption("t", "type", true, "Type");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            e.printStackTrace();
            System.exit(1);
            return;
        }

        String name = cmd.getOptionValue("name");
        String identifier = cmd.getOptionValue("identifier");
        String country = cmd.getOptionValue("country");
        String address = cmd.getOptionValue("address");
        String dob = cmd.getOptionValue("dob");
        String contact = cmd.getOptionValue("contact");
        String type = cmd.getOptionValue("type", "company");

        // Check required conditions
        // Equivalent logic: if not ((args.name and args.address) or (args.name and args.country)
        // or (args.name and args.identifier) or (args.name))
        if (!((name != null && !name.isEmpty() && address != null && !address.isEmpty()) ||
              (name != null && !name.isEmpty() && country != null && !country.isEmpty()) ||
              (name != null && !name.isEmpty() && identifier != null && !identifier.isEmpty()) ||
              (name != null && !name.isEmpty()))) {
            System.err.println("Not enough information provided to match the entity");
            System.exit(1);
        }

        try {
            String token = getAuthToken();
            String query = assembleQueryString(name, identifier, country, address, dob, contact, type);

            JSONObject resolved = resolveEntity(token, query);
            JSONArray dataArr = resolved.optJSONArray("data");
            if (dataArr == null) {
                System.out.println("No matches found.");
                return;
            }

            for (int i = 0; i < dataArr.length(); i++) {
                JSONObject r = dataArr.getJSONObject(i);
                String label = r.optString("label");
                String entityId = r.optString("entity_id");
                JSONObject matchStrength = r.optJSONObject("match_strength");
                String strengthValue = (matchStrength != null) ? matchStrength.optString("value") : "unknown";

                System.out.println("Sayari matched " + label + " with an id of " + entityId + " at a strength of " + strengthValue + "\n");

                JSONObject e = getEntity(token, entityId);
                JSONObject risk = e.optJSONObject("risk");
                if (risk == null) {
                    risk = new JSONObject();
                }
                Set<String> riskKeys = e.optJSONObject("risk") != null ? e.optJSONObject("risk").keySet() : Collections.emptySet();
                
                if (e.has("latest_status")) {
                    JSONObject latestStatus = e.optJSONObject("latest_status");
                    String status = (latestStatus != null) ? latestStatus.optString("status") : "unknown";
                    System.out.println("Sayari identified risks of " + riskKeys + ". " + e.optString("label") + " latest status is " + status + ".\n");
                } else {
                    System.out.println("Sayari identified risks of " + riskKeys + ". " + e.optString("label") + " latest status is unknown.\n");
                }

                List<Map<String, Map<String, Object>>> identifiedUbo = new ArrayList<>();
                int offset = 0;
                JSONObject u = getUbo(token, e.getString("id"), offset);
                JSONArray uDataArr = u.optJSONArray("data");
                if (uDataArr != null) {
                    for (int j = 0; j < uDataArr.length(); j++) {
                        JSONObject iObj = uDataArr.getJSONObject(j);
                        JSONObject target = iObj.optJSONObject("target");
                        if (target != null) {
                            String targetId = target.optString("id");
                            String targetLabel = target.optString("label");
                            ArrayList<String> targetAddresses = new ArrayList<String>();
                            JSONArray jArray = target.optJSONArray("addresses");
                            if (jArray != null && jArray.length() > 0) {
                                for (int k = 0; k < jArray.length(); k++){  
                                    targetAddresses.add(jArray.getString(k));
                                }
                            }
                            Set<String> targetRisks = target.optJSONObject("risk") != null ? target.optJSONObject("risk").keySet() : Collections.emptySet();
                            Map<String, Object> innerMap = new HashMap<>();
                            innerMap.put("name", targetLabel);
                            innerMap.put("risks", new ArrayList<>(targetRisks));
                            innerMap.put("addresses", targetAddresses);
                            Map<String, Map<String, Object>> outerMap = new HashMap<>();
                            outerMap.put(targetId, innerMap);
                            identifiedUbo.add(outerMap);
                        }
                    }
                }

                // Check if there's a 'next'
                boolean hasNext = u.optBoolean("next", false);
                while (hasNext && offset <= 9950) {
                    offset += 50;
                    u = getUbo(token, e.getString("id"), offset);
                    uDataArr = u.optJSONArray("data");
                    if (uDataArr != null) {
                        for (int j = 0; j < uDataArr.length(); j++) {
                            JSONObject iObj = uDataArr.getJSONObject(j);
                            JSONObject target = iObj.optJSONObject("target");
                            if (target != null) {
                                String targetId = target.optString("id");
                                String targetLabel = target.optString("label");
                                ArrayList<String> targetAddresses = new ArrayList<String>();
                                JSONArray jArray = target.optJSONArray("addresses");
                                if (jArray != null && jArray.length() > 0) {
                                    for (int k = 0; k < jArray.length(); k++){  
                                        targetAddresses.add(jArray.getString(k));
                                    }
                                }
                                Set<String> targetRisks = target.optJSONObject("risk") != null ? target.optJSONObject("risk").keySet() : Collections.emptySet();
                                Map<String, Object> innerMap = new HashMap<>();
                                innerMap.put("name", targetLabel);
                                innerMap.put("risks", new ArrayList<>(targetRisks));
                                innerMap.put("addresses", targetAddresses);
                                Map<String, Map<String, Object>> outerMap = new HashMap<>();
                                outerMap.put(targetId, innerMap);
                                identifiedUbo.add(outerMap);
                            }
                        }
                    }
                    hasNext = u.optBoolean("next", false);
                }

                System.out.println("The Sayari identified UBO and their risks for " + label + " with an id of " + entityId + " is " + identifiedUbo + "\n");
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
