package io.github.nickm980.smallville.llm.api;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.nickm980.smallville.Settings;
import io.github.nickm980.smallville.exceptions.SmallvilleException;
import io.github.nickm980.smallville.llm.Prompt;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ChatGPT implements LLM {
    private final static String REQUEST_PATH = "https://api.openai.com/v1/chat/completions";
    private final static Logger LOG = LoggerFactory.getLogger(ChatGPT.class);
    private final static ObjectMapper MAPPER = new ObjectMapper();

    // ["choices"]["message"]["content"]
    @Override
    public String sendChat(Prompt prompt, double temperature) {
	OkHttpClient client = new OkHttpClient();

	String json = """
		{
			"model": "gpt-3.5-turbo",
			"messages": [
				%messages
			], "temperature": %temperature, "max_tokens": 1000
		}
		""";

	try {
	    json = json.replaceAll("\n", "");
	    json = json.replaceAll("\t", "");
	    json = json.strip();
	    json = json.replace("%messages", MAPPER.writeValueAsString(prompt.build()));
	    json = json.replace("%temperature", String.valueOf(temperature));

	} catch (JsonProcessingException e1) {
	    e1.printStackTrace();
	}

	LOG.info(json);

	RequestBody body = RequestBody.create(json.getBytes());
	Request request = new Request.Builder()
	    .url(REQUEST_PATH)
	    .addHeader("Content-Type", "application/json")
	    .addHeader("Authorization", "Bearer " + Settings.getApiKey())
	    .post(body)
	    .build();

	String result = "";

	try {
	    Response response = client.newCall(request).execute();
	    String responseBody = response.body().string();

	    ObjectMapper objectMapper = new ObjectMapper();
	    JsonNode node = objectMapper.readTree(responseBody);

	    if (node.get("choices") == null) {
		LOG.error("Invalid api token or rate limit reached");
		throw new SmallvilleException("Invalid api token or rate limit reached.");
	    }

	    LOG.info(responseBody.replace("\n", ""));

	    result = node.get("choices").get(0).get("message").get("content").asText();
	} catch (IOException e) {
	    e.printStackTrace();
	}

	return result;
    }

    @Override
    public float[] getTokenEmbeddings(String text) {
	OkHttpClient client = new OkHttpClient();
	ObjectMapper mapper = new ObjectMapper();
	float[] result = new float[0];

	try {
	    // Create the request body
	    JsonNode requestBody = mapper.createObjectNode().put("model", "text-embedding-ada-002").put("input", text);

	    Request request = new Request.Builder()
		.url("https://api.openai.com/v1/embeddings")
		.post(RequestBody
		    .create(mapper.writeValueAsString(requestBody), okhttp3.MediaType.parse("application/json")))
		.addHeader("Authorization", "Bearer " + Settings.getApiKey())
		.build();

	    Response response = client.newCall(request).execute();
	    String responseBody = response.body().string();
	    JsonNode responseJson = mapper.readTree(responseBody);

	    result = mapper.convertValue(responseJson.get("data").get(0).get("embedding"), float[].class);
	} catch (IOException e) {
	    e.printStackTrace();
	}

	return result;
    }
}