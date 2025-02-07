package io.github.nickm980.smallville.llm;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.NotImplementedException;

import io.github.nickm980.smallville.exceptions.SmallvilleException;
import io.github.nickm980.smallville.llm.update.AtomicPromptBuilder;
import io.github.nickm980.smallville.models.ActionHistory;
import io.github.nickm980.smallville.models.Agent;
import io.github.nickm980.smallville.models.Conversation;
import io.github.nickm980.smallville.models.Location;
import io.github.nickm980.smallville.models.SimulatedLocation;

public class PromptBuilder implements IPromptBuilder {

    private PromptData data;
    private AtomicPromptBuilder atomicBuilder;
    private String prompt;

    public PromptBuilder() {
	this.atomicBuilder = new AtomicPromptBuilder();
	this.data = new PromptData();
    }

    @Override
    public PromptBuilder withAgent(Agent agent) {
	data.setAgent(agent);
	return this;
    }

    @Override
    public PromptBuilder withLocations(List<? extends Location> locations) {
	data.setLocations(locations);
	return this;
    }

    @Override
    public PromptBuilder withConversation(Conversation conversation) {
	data.setConversation(conversation);
	return this;
    }

    @Override
    public PromptBuilder createConversationWith(Agent other) {
	// TODO: add relevant memories
	prompt = """
		[Agent Summary Description]

		%other_summary_description%

		First, decide whether or not [Agent Name] is going to initiate a conversation.

		If [Agent Name] is not going to initiate a conversation respond with "No conversation"

		Otherwise, create a fake conversation between [Agent Name] and %other_name%

		For example, a conversation between people named A and B would look like this

		A: Hello, how are you today?
		B: I am good, and you?
		""";

	prompt
	    .replace("%other_summary_description%", atomicBuilder.getAgentSummaryDescription(other))
	    .replace("%other_name%", other.getFullName());
	return this;
    }

    @Override
    public PromptBuilder createAgentLocationStatePrompt(String pastAndPresentStatus) {
	prompt = """
		All objects in the world: %objects%

		John's status: %status%

		What happens to the state of the objects?

		For example, if a door turns from closed to open respond with
		{
		    "object": "door",
		    "state": "open"
		}

		[
		 {
		    "object": "<fill in>",
		    "state": "<fill in>"
		 },
		 {
		    "object": "<fill in>",
		    "state": "<fill in>"
		 }
		]
		""";

	prompt = prompt.replace("%objects%", atomicBuilder.asNaturalLanguage(data.getLocations()));
	prompt = prompt.replace("%status%", pastAndPresentStatus);

	return this;
    }

    @Override
    public PromptBuilder createReactionSuggestion(String observation) {
	prompt = """
		[World Description]
		[Agent Summary Description]
		John Lin’s status: %current_activity%
		Observation: %observation%
		Summary of relevant context from [Agent Name]’s memory: %relevant_memories%

		Should [Agent Name] react to the observation, and if so, what would the reaction be?

		* If the reaction is to start a conversation set the conservation field to true
		* Prefer to stay in a close by area

		Respond in the following JSON format:

		{
		  "react": "<yes/no>",
		  "action": "I am <your current activity>",
		  "location": "<your current location>",
		  "emoji": "<pick an emoji to represent your current activity>",
		}
					""";

	prompt = prompt
	    .replace("%current_activity%", data.getAgent().getCurrentActivity())
	    .replace("%relevant_memories%", atomicBuilder.buildRelevantMemories(data.getAgent(), observation))
	    .replace("%observation%", observation);

	return this;
    }

    @Override
    public PromptBuilder createCurrentPlanPrompt() {
	prompt = """
		[World Description]
		[Agent Summary Description]
		[Future Plans]

		What are you doing now? You will always be engaged in an activity.
		For last_activity fill in the past tense of this sentence: [Current Activity]
		Respond confidently in the following JSON format

		{
		  "last_activity": "<fill in>"
		  "location": "<fill in>",
		  "activity": "I am <fill in>",
		  "emoji": "<fill in>"
		}
		""";

	return this;
    }

    @Override
    public PromptBuilder createMemoryRankPrompt() {
	prompt = """
		On the scale of 1 to 10, where 1 is purely mundane
		(e.g., brushing teeth, making bed) and 10 is extremely poignant (e.g., a break up, college
		acceptance), rate the likely poignancy of the following pieces of memory. Always answer with only a list of numbers. For example, if given the following memories Memories: John did nothing, John lin went to school, John lin saw a concert
		respond with [1, 3, 5]. If just given one memory still respond in a list. Memories are separated by commas. Memories: %s
		""";

	prompt = String
	    .format(prompt,
		    data
			.getAgent()
			.getMemoryStream()
			.getUnweightedMemories()
			.stream()
			.map(memory -> memory.getDescription())
			.collect(Collectors.joining(", ")));
	return this;
    }

    @Override
    public PromptBuilder createFuturePlansPrompt(TimePhrase time) {
	this.prompt = """
		[Agent Summary Description]
		Based on what you know and the current time, guess what [Agent Name] will do for the rest of the day

		For example:
		1. Walk to the school from 9am-9:30am
		2. Take a test from 12pm-12:30pm
		3. Go to lunch from 2pm-3pm
		4. Work on a project after school from 2:30pm-3pm
		5. Have dinner from 5pm to 5:30pm

		Respond using a bullet list of 5 activities and times. Do not use pronouns.
					""";

	return this;
    }

    @Override
    public PromptBuilder createAskQuestionPrompt(String question) {
	prompt = """
		[World Description]
		[Agent Summary Description]
		%relevant_memories%

		Pretend you are [Agent Name] and answer the following question
		in the first person: %question%
		""";

	prompt = prompt
	    .replace("%question%", question)
	    .replace("%relevant_memories%", atomicBuilder.buildRelevantMemories(data.getAgent(), question));

	return this;
    }

    public PromptBuilder createPastAndPresent() {
	prompt = """
		  John Lin's current status: [Current Activity]
		  John Lin's past status: [Last Activity]

		  Format the current and past activity to say x is no longer {past status} and is now {current status}
		""";

	return this;
    }

    @Override
    public Prompt build() {
	if (prompt == null || prompt.isEmpty()) {
	    throw new SmallvilleException("Must call a creation function to make a new prompt first");
	}

	Agent agent = data.getAgent();

	prompt = prompt
	    .replace("[World Description]", atomicBuilder.getWorldDescription(data.getLocations()))
	    .replace("[Agent Summary Description]", atomicBuilder.getAgentSummaryDescription(agent))
	    .replace("[Current Time]", atomicBuilder.getTimeAsString(LocalDateTime.now()))
	    .replace("[Agent Name]", agent.getFullName())
	    .replace("[Current Activity]", agent.getCurrentActivity())
	    .replace("[Last Activity]", agent.getLastActivity())
	    .replace("[Future Plans]", "Plans: " + atomicBuilder.asNaturalLanguage(agent.getPlans()));

	return new Prompt.User(prompt);
    }

    public PromptBuilder createObjectUpdates(String tenses) {
	prompt = """
		[World Description]
		Description: %tenses%

		What are the new states of the locations?
		Respond in the following format:

		<Object>: <State>

		For example, if John is no longer cooking coffee and is now taking a shower
		Coffee Machine: Off
		Shower: On

		Do not include locations which have not been changed.
		""";
	return this;
    }
}