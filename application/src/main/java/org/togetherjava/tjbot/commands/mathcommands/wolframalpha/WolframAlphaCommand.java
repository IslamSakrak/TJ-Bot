package org.togetherjava.tjbot.commands.mathcommands.wolframalpha;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.mikael.urlbuilder.UrlBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageUpdateAction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.config.Config;

import java.io.IOException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;

public final class WolframAlphaCommand extends SlashCommandAdapter {
    public static final int HTTP_STATUS_CODE_OK = 200;
    private static final XmlMapper XML = new XmlMapper();
    private static final String QUERY_OPTION = "query";
    /**
     * WolframAlpha API endpoint to connect to.
     *
     * @see <a href=
     *      "https://products.wolframalpha.com/docs/WolframAlpha-API-Reference.pdf">WolframAlpha API
     *      Reference</a>.
     */
    private static final String API_ENDPOINT = "http://api.wolframalpha.com/v2/query";
    private static final Logger logger = LoggerFactory.getLogger(WolframAlphaCommand.class);
    private final HttpClient client = HttpClient.newHttpClient();

    public WolframAlphaCommand() {
        super("wolf", "Renders mathematical queries using WolframAlpha",
                SlashCommandVisibility.GUILD);
        getData().addOption(OptionType.STRING, QUERY_OPTION, "the query to send to WolframAlpha",
                true);
    }


    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {

        // The processing takes some time
        event.deferReply().queue();

        String query = Objects.requireNonNull(event.getOption(QUERY_OPTION)).getAsString();

        // Send query
        HttpRequest request = HttpRequest
            .newBuilder(UrlBuilder.fromString(API_ENDPOINT)
                .addParameter("appid", Config.getInstance().getWolframAlphaAppId())
                .addParameter("format", "image,plaintext")
                .addParameter("input", query)
                .toUri())
            .GET()
            .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            event.getHook()
                .setEphemeral(true)
                .editOriginal("Unable to get a response from the server")
                .queue();
            logger.error("Could not get the response from the server", e);
            return;
        }

        if (response.statusCode() != HTTP_STATUS_CODE_OK) {
            event.getHook()
                .setEphemeral(true)
                .editOriginal("The response' status code was incorrect")
                .queue();
            logger.warn("Unexpected status code: Expected: {} Actual: {}", HTTP_STATUS_CODE_OK,
                    response.statusCode());
            return;
        }

        // Parse query
        QueryResult result;
        try {
            result = XML.readValue(response.body(), QueryResult.class);
        } catch (JsonProcessingException e) {
            event.getHook()
                .setEphemeral(true)
                .editOriginal("Could not serialize the XML recieved")
                .queue();
            logger.error("Error in serializing the class ", e);
            return;
        }

        if (!result.isSuccess()) {
            event.getHook()
                .setEphemeral(true)
                .editOriginal("Could not successfully receive the result")
                .queue();
            logger.error("Not a successful result ");

            // TODO The exact error details have a different POJO structure,
            // POJOs have to be added to get those details. See the Wolfram doc.
            return;
        }


        // Create result
        WebhookMessageUpdateAction<Message> action =
                event.getHook().editOriginal("Computed in: " + result.getTiming());
        for (Pod pod : result.getPods()) {
            for (SubPod subPod : pod.getSubPods()) {
                Image image = subPod.getImage();
                try {
                    String name = image.getTitle();
                    if (name.isEmpty()) {
                        name = pod.getTitle();
                    }
                    // TODO Figure out how to tell JDA that those are gifs (but sometimes also JPEG,
                    // see Wolfram doc)
                    action = action.addFile(new URL(image.getSource()).openStream(), name);
                } catch (IOException e) {
                    event.reply("There was an error in generating the images")
                        .setEphemeral(true)
                        .queue();
                    logger.error("Could not get image source url ", e);
                    return;
                }
            }
        }
        action.queue();
    }
}