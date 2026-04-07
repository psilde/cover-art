package com.psilde;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class SpotifyService {

    @Value("${spotify.client-id}")
    private String clientId;

    @Value("${spotify.client-secret}")
    private String clientSecret;

    private final RestTemplate restTemplate = new RestTemplate();
    private String accessToken;
    private long tokenExpiry = 0;

    private String getAccessToken() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpiry) {
            return accessToken;
        }

        String credentials = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Authorization", "Basic " + credentials);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");

        ResponseEntity<Map> response = restTemplate.postForEntity(
            "https://accounts.spotify.com/api/token",
            new HttpEntity<>(body, headers),
            Map.class
        );

        Map<String, Object> responseBody = response.getBody();
        accessToken = (String) responseBody.get("access_token");
        int expiresIn = (int) responseBody.get("expires_in");
        tokenExpiry = System.currentTimeMillis() + (expiresIn - 60) * 1000L;

        return accessToken;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getImages(String spotifyUrl) {
        // Strip query params and trailing slashes
        String path = spotifyUrl.replaceAll("\\?.*", "").replaceAll("/$", "");
        String[] parts = path.split("/");

        if (parts.length < 5) {
            throw new IllegalArgumentException("Invalid Spotify URL");
        }

        String type = parts[3];
        String id = parts[4];

        if (!type.equals("track") && !type.equals("album")) {
            throw new IllegalArgumentException("URL must be a Spotify track or album link");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + getAccessToken());
        HttpEntity<Void> request = new HttpEntity<>(headers);

        String apiUrl = "https://api.spotify.com/v1/" + type + "s/" + id;
        ResponseEntity<Map> response = restTemplate.exchange(apiUrl, HttpMethod.GET, request, Map.class);
        Map<String, Object> data = response.getBody();

        List<Map<String, Object>> images;
        String name = (String) data.get("name");
        String artist;

        if (type.equals("track")) {
            Map<String, Object> album = (Map<String, Object>) data.get("album");
            images = (List<Map<String, Object>>) album.get("images");
        } else {
            images = (List<Map<String, Object>>) data.get("images");
        }

        List<Map<String, Object>> artists = (List<Map<String, Object>>) data.get("artists");
        artist = artists.isEmpty() ? "Unknown" : (String) artists.get(0).get("name");

        return Map.of("name", name, "artist", artist, "images", images, "type", type);
    }
}
