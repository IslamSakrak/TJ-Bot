package org.togetherjava.tjbot.commands.tags;

import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.togetherjava.tjbot.commands.SlashCommand;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.Tags;
import org.togetherjava.tjbot.jda.JdaTester;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

final class TagManageCommandTest {
    private TagSystem system;
    private JdaTester jdaTester;
    private SlashCommand command;
    private Member moderator;

    private static @NotNull MessageEmbed getResponse(@NotNull SlashCommandEvent event) {
        ArgumentCaptor<MessageEmbed> responseCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(responseCaptor.capture());
        return responseCaptor.getValue();
    }

    @BeforeEach
    void setUp() {
        Config config = mock(Config.class);
        String moderatorRoleName = "Moderator";
        when(config.getTagManageRolePattern()).thenReturn(moderatorRoleName);

        Database database = Database.createMemoryDatabase(Tags.TAGS);
        system = spy(new TagSystem(database));
        jdaTester = new JdaTester();
        command = new TagManageCommand(system, config);

        moderator = jdaTester.createMemberSpy(1);
        Role moderatorRole = mock(Role.class);
        doReturn(true).when(moderator).hasPermission(any(Permission.class));
        doReturn(List.of(moderatorRole)).when(moderator).getRoles();
        when(moderatorRole.getName()).thenReturn(moderatorRoleName);
    }

    private @NotNull SlashCommandEvent triggerRawCommand(@NotNull String tagId) {
        return triggerRawCommandWithUser(tagId, moderator);
    }

    private @NotNull SlashCommandEvent triggerRawCommandWithUser(@NotNull String tagId,
            @NotNull Member user) {
        SlashCommandEvent event = jdaTester.createSlashCommandEvent(command)
            .setSubcommand(TagManageCommand.Subcommand.RAW.getName())
            .setOption(TagManageCommand.ID_OPTION, tagId)
            .setUserWhoTriggered(user)
            .build();

        command.onSlashCommand(event);
        return event;
    }

    private @NotNull SlashCommandEvent triggerCreateCommand(@NotNull String tagId,
            @NotNull String content) {
        return triggerTagContentCommand(TagManageCommand.Subcommand.CREATE, tagId, content);
    }

    private @NotNull SlashCommandEvent triggerEditCommand(@NotNull String tagId,
            @NotNull String content) {
        return triggerTagContentCommand(TagManageCommand.Subcommand.EDIT, tagId, content);
    }

    private @NotNull SlashCommandEvent triggerTagContentCommand(
            @NotNull TagManageCommand.Subcommand subcommand, @NotNull String tagId,
            @NotNull String content) {
        SlashCommandEvent event = jdaTester.createSlashCommandEvent(command)
            .setSubcommand(subcommand.getName())
            .setOption(TagManageCommand.ID_OPTION, tagId)
            .setOption(TagManageCommand.CONTENT_OPTION, content)
            .setUserWhoTriggered(moderator)
            .build();

        command.onSlashCommand(event);
        return event;
    }

    private @NotNull SlashCommandEvent triggerCreateWithMessageCommand(@NotNull String tagId,
            @NotNull String messageId) {
        return triggerTagMessageCommand(TagManageCommand.Subcommand.CREATE_WITH_MESSAGE, tagId,
                messageId);
    }

    private @NotNull SlashCommandEvent triggerEditWithMessageCommand(@NotNull String tagId,
            @NotNull String messageId) {
        return triggerTagMessageCommand(TagManageCommand.Subcommand.EDIT_WITH_MESSAGE, tagId,
                messageId);
    }

    private @NotNull SlashCommandEvent triggerTagMessageCommand(
            @NotNull TagManageCommand.Subcommand subcommand, @NotNull String tagId,
            @NotNull String messageId) {
        SlashCommandEvent event = jdaTester.createSlashCommandEvent(command)
            .setSubcommand(subcommand.getName())
            .setOption(TagManageCommand.ID_OPTION, tagId)
            .setOption(TagManageCommand.MESSAGE_ID_OPTION, messageId)
            .setUserWhoTriggered(moderator)
            .build();

        command.onSlashCommand(event);
        return event;
    }

    private @NotNull SlashCommandEvent triggerDeleteCommand(@NotNull String tagId) {
        SlashCommandEvent event = jdaTester.createSlashCommandEvent(command)
            .setSubcommand(TagManageCommand.Subcommand.DELETE.getName())
            .setOption(TagManageCommand.ID_OPTION, tagId)
            .setUserWhoTriggered(moderator)
            .build();

        command.onSlashCommand(event);
        return event;
    }

    private void postMessage(@NotNull String content, @NotNull String id) {
        Message message = new MessageBuilder(content).build();
        doReturn(jdaTester.createSucceededActionMock(message)).when(jdaTester.getTextChannelSpy())
            .retrieveMessageById(id);
    }

    private void failOnRetrieveMessage(@NotNull String messageId, @NotNull Throwable failure) {
        doReturn(jdaTester.createFailedActionMock(failure)).when(jdaTester.getTextChannelSpy())
            .retrieveMessageById(messageId);
    }

    @Test
    @DisplayName("Users without the required role can not use '/tag-manage'")
    void commandCanNotBeUsedWithoutRoles() {
        // GIVEN a regular user without roles
        Member regularUser = jdaTester.createMemberSpy(1);

        // WHEN the regular user triggers any '/tag-manage' command
        SlashCommandEvent event = triggerRawCommandWithUser("foo", regularUser);

        // THEN the command can not be used since the user lacks roles
        verify(event).reply("Tags can only be managed by users with a corresponding role.");
    }

    @Test
    @DisplayName("'/tag-manage raw' can not be used on unknown tags")
    void rawTagCanNotFindUnknownTag() {
        // GIVEN a tag system without any tags
        // WHEN using '/tag-manage raw id:unknown'
        SlashCommandEvent event = triggerRawCommand("unknown");

        // THEN the command can not find the tag and responds accordingly
        verify(event).reply(startsWith("Could not find any tag"));
    }

    @Test
    @DisplayName("'/tag-manage raw' shows the raw content of a known tag")
    void rawTagShowsContentIfFound() {
        // GIVEN a tag system with the "foo" tag
        system.putTag("foo", "bar");

        // WHEN using '/tag-manage raw id:foo'
        triggerRawCommand("foo");

        // THEN the command responds with its content as an attachment
        verify(jdaTester.getReplyActionMock())
            .addFile(aryEq("bar".getBytes(StandardCharsets.UTF_8)), anyString());
    }

    @Test
    @DisplayName("'/tag-manage create' fails if the tag already exists")
    void createTagThatAlreadyExistsFails() {
        // GIVEN a tag system with the "foo" tag
        system.putTag("foo", "old");

        // WHEN using '/tag-manage create id:foo content:new'
        SlashCommandEvent event = triggerCreateCommand("foo", "new");

        // THEN the command fails and responds accordingly, the tag is still there and unchanged
        verify(event).reply("The tag with id 'foo' already exists.");
        assertTrue(system.hasTag("foo"));
        assertEquals("old", system.getTag("foo").orElseThrow());
    }

    @Test
    @DisplayName("'/tag-manage create' works if the tag is new")
    void createNewTagWorks() {
        // GIVEN a tag system without any tags
        // WHEN using '/tag-manage create id:foo content:bar'
        SlashCommandEvent event = triggerCreateCommand("foo", "bar");

        // THEN the command succeeds and the system contains the tag
        assertEquals("Success", getResponse(event).getTitle());
        assertTrue(system.hasTag("foo"));
        assertEquals("bar", system.getTag("foo").orElseThrow());
    }

    @Test
    @DisplayName("'/tag-manage edit' fails if the tag is unknown")
    void editUnknownTagFails() {
        // GIVEN a tag system without any tags
        // WHEN using '/tag-manage edit id:foo content:new'
        SlashCommandEvent event = triggerEditCommand("foo", "new");

        // THEN the command fails and responds accordingly, the tag was not created
        verify(event).reply(startsWith("Could not find any tag with id"));
        assertFalse(system.hasTag("foo"));
    }

    @Test
    @DisplayName("'/tag-manage edit' works if the tag is known")
    void editExistingTagWorks() {
        // GIVEN a tag system with the "foo" tag
        system.putTag("foo", "old");

        // WHEN using '/tag-manage edit id:foo content:new'
        SlashCommandEvent event = triggerEditCommand("foo", "new");

        // THEN the command succeeds and the content of the tag was changed
        assertEquals("Success", getResponse(event).getTitle());
        assertEquals("new", system.getTag("foo").orElseThrow());
    }

    @Test
    @DisplayName("'/tag-manage delete' fails if the tag is unknown")
    void deleteUnknownTagFails() {
        // GIVEN a tag system without any tags
        // WHEN using '/tag-manage delete id:foo'
        SlashCommandEvent event = triggerDeleteCommand("foo");

        // THEN the command fails and responds accordingly
        verify(event).reply(startsWith("Could not find any tag with id"));
    }

    @Test
    @DisplayName("'/tag-manage delete' works if the tag is known")
    void deleteExistingTagWorks() {
        // GIVEN a tag system with the "foo" tag
        system.putTag("foo", "bar");

        // WHEN using '/tag-manage delete id:foo'
        SlashCommandEvent event = triggerDeleteCommand("foo");

        // THEN the command succeeds and the tag was deleted
        assertEquals("Success", getResponse(event).getTitle());
        assertFalse(system.hasTag("foo"));
    }

    @Test
    @DisplayName("'/tag-manage create-with-message' fails if the given message id is in an invalid format")
    void createWithMessageFailsForInvalidMessageId() {
        // GIVEN a tag system without any tags
        // WHEN using '/tag-manage create-with-message id:foo message-id:bar'
        SlashCommandEvent event = triggerCreateWithMessageCommand("foo", "bar");

        // THEN the command fails and responds accordingly, the tag was not created
        verify(event).reply("The given message id 'bar' is invalid, expected a number.");
        assertFalse(system.hasTag("foo"));
    }

    @Test
    @DisplayName("'/tag-manage create-with-message' fails if the tag is known")
    void createWithMessageTagThatAlreadyExistsFails() {
        // GIVEN a tag system with the "foo" tag and a message with id and content
        system.putTag("foo", "old");
        postMessage("new", "1");

        // WHEN using '/tag-manage create-with-message id:foo message-id:1'
        SlashCommandEvent event = triggerCreateWithMessageCommand("foo", "1");

        // THEN the command fails and responds accordingly, the tag is still there and unchanged
        verify(event).reply("The tag with id 'foo' already exists.");
        assertTrue(system.hasTag("foo"));
        assertEquals("old", system.getTag("foo").orElseThrow());
    }

    @Test
    @DisplayName("'/tag-manage create-with-message' works if the tag is new")
    void createWithMessageNewTagWorks() {
        // GIVEN a tag system without any tags and a message with id and content
        postMessage("bar", "1");

        // WHEN using '/tag-manage create-with-message id:foo message-id:1'
        SlashCommandEvent event = triggerCreateWithMessageCommand("foo", "1");

        // THEN the command succeeds and the system contains the tag
        assertEquals("Success", getResponse(event).getTitle());
        assertTrue(system.hasTag("foo"));
        assertEquals("bar", system.getTag("foo").orElseThrow());
    }

    @Test
    @DisplayName("'/tag-manage create-with-message' fails if the linked message is unknown")
    void createWithMessageUnknownMessageFails() {
        // GIVEN a tag system without any tags and an unknown message id
        failOnRetrieveMessage("1",
                jdaTester.createErrorResponseException(ErrorResponse.UNKNOWN_MESSAGE));

        // WHEN using '/tag-manage create-with-message id:foo message-id:1'
        SlashCommandEvent event = triggerCreateWithMessageCommand("foo", "1");

        // THEN the command fails and responds accordingly, the tag was not created
        verify(event).reply("The message with id '1' does not exist.");
        assertFalse(system.hasTag("foo"));
    }

    @Test
    @DisplayName("'/tag-manage create-with-message' fails if there is a generic error (such as a network failure)")
    void createWithMessageGenericErrorFails() {
        // GIVEN a tag system without any tags and a generic network failure
        failOnRetrieveMessage("1", new IOException("Generic network failure"));

        // WHEN using '/tag-manage create-with-message id:foo message-id:1'
        SlashCommandEvent event = triggerCreateWithMessageCommand("foo", "1");

        // THEN the command fails and responds accordingly, the tag was not created
        verify(event).reply(startsWith("Something unexpected went wrong"));
        assertFalse(system.hasTag("foo"));
    }

    @Test
    @DisplayName("'/tag-manage edit-with-message' fails if the given message id is in an invalid format")
    void editWithMessageFailsForInvalidMessageId() {
        // GIVEN a tag system with the "foo" tag
        system.putTag("foo", "old");

        // WHEN using '/tag-manage edit-with-message id:foo message-id:new'
        SlashCommandEvent event = triggerEditWithMessageCommand("foo", "bar");

        // THEN the command fails and responds accordingly, the tags content was not changed
        verify(event).reply("The given message id 'bar' is invalid, expected a number.");
        assertEquals("old", system.getTag("foo").orElseThrow());
    }

    @Test
    @DisplayName("'/tag-manage edit-with-message' fails if the tag is unknown")
    void editWithMessageUnknownTagFails() {
        // GIVEN a tag system without any tags
        postMessage("bar", "1");

        // WHEN using '/tag-manage edit-with-message id:foo message-id:new'
        SlashCommandEvent event = triggerEditWithMessageCommand("foo", "1");

        // THEN the command fails and responds accordingly, the tag was not created
        verify(event).reply(startsWith("Could not find any tag with id"));
        assertFalse(system.hasTag("foo"));
    }

    @Test
    @DisplayName("'/tag-manage edit-with-message' works if the tag is known")
    void editWithMessageExistingTagWorks() {
        // GIVEN a tag system with the "foo" tag
        system.putTag("foo", "old");
        postMessage("new", "1");

        // WHEN using '/tag-manage edit-with-message id:foo message-id:1'
        SlashCommandEvent event = triggerEditWithMessageCommand("foo", "1");

        // THEN the command succeeds and the content of the tag was changed
        assertEquals("Success", getResponse(event).getTitle());
        assertEquals("new", system.getTag("foo").orElseThrow());
    }

    @Test
    @DisplayName("'/tag-manage edit-with-message' fails if the linked message is unknown")
    void editWithMessageUnknownMessageFails() {
        // GIVEN a tag system with the "foo" tag and an unknown message id
        system.putTag("foo", "old");
        failOnRetrieveMessage("1",
                jdaTester.createErrorResponseException(ErrorResponse.UNKNOWN_MESSAGE));

        // WHEN using '/tag-manage edit-with-message id:foo message-id:1'
        SlashCommandEvent event = triggerEditWithMessageCommand("foo", "1");

        // THEN the command fails and responds accordingly, the tag has not changed
        verify(event).reply("The message with id '1' does not exist.");
        assertTrue(system.hasTag("foo"));
        assertEquals("old", system.getTag("foo").orElseThrow());
    }

    @Test
    @DisplayName("'/tag-manage edit-with-message' fails if there is a generic error (such as a network failure)")
    void editWithMessageGenericErrorFails() {
        // GIVEN a tag system with the "foo" tag and a generic network failure
        system.putTag("foo", "old");
        failOnRetrieveMessage("1", new IOException("Generic network failure"));

        // WHEN using '/tag-manage edit-with-message id:foo message-id:1'
        SlashCommandEvent event = triggerEditWithMessageCommand("foo", "1");

        // THEN the command fails and responds accordingly, the tag has not changed
        verify(event).reply(startsWith("Something unexpected went wrong"));
        assertTrue(system.hasTag("foo"));
        assertEquals("old", system.getTag("foo").orElseThrow());
    }
}
