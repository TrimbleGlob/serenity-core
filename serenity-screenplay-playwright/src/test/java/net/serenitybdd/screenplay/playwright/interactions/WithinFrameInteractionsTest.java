package net.serenitybdd.screenplay.playwright.interactions;

import net.serenitybdd.junit5.SerenityJUnit5Extension;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.playwright.Target;
import net.serenitybdd.screenplay.playwright.abilities.BrowseTheWebWithPlaywright;
import net.serenitybdd.screenplay.playwright.questions.Text;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for WithinFrame interactions.
 * These tests verify that actors can interact with elements inside iframes.
 */
@ExtendWith(SerenityJUnit5Extension.class)
public class WithinFrameInteractionsTest {

    Actor alice;

    private static String fileUrl(String filename) {
        Path path = Paths.get("src/test/resources/files/" + filename).toAbsolutePath();
        return "file://" + path;
    }

    @BeforeEach
    void setup() {
        alice = Actor.named("Alice")
            .whoCan(BrowseTheWebWithPlaywright.usingTheDefaultConfiguration());
    }

    @Test
    void should_click_element_within_frame() {
        alice.attemptsTo(
            Open.url(fileUrl("iframe-test.html")),
            WithinFrame.locatedBy("#editor-frame")
                .click("body#editor")
        );

        // Verify we can interact with frame content
        Target frameContent = Target.the("frame content")
            .inFrame("#editor-frame")
            .locatedBy("body#editor");

        String text = alice.asksFor(Text.of(frameContent));
        assertThat(text).isNotEmpty();
    }

    @Test
    void should_fill_input_within_frame() {
        alice.attemptsTo(
            Open.url(fileUrl("iframe-test.html")),
            // Clear existing content first
            WithinFrame.locatedBy("#editor-frame")
                .clear("body#editor"),
            // Fill with new text
            WithinFrame.locatedBy("#editor-frame")
                .fill("body#editor", "Hello from Serenity!")
        );

        // Verify the text was entered
        Target frameContent = Target.the("frame content")
            .inFrame("#editor-frame")
            .locatedBy("body#editor");

        String text = alice.asksFor(Text.of(frameContent));
        assertThat(text).contains("Hello from Serenity!");
    }

    @Test
    void should_type_text_within_frame() {
        alice.attemptsTo(
            Open.url(fileUrl("iframe-test.html")),
            // Clear existing content
            WithinFrame.locatedBy("#editor-frame")
                .clear("body#editor"),
            // Type text character by character
            WithinFrame.locatedBy("#editor-frame")
                .type("body#editor", "Typed text")
        );

        // Verify the text was typed
        Target frameContent = Target.the("frame content")
            .inFrame("#editor-frame")
            .locatedBy("body#editor");

        String text = alice.asksFor(Text.of(frameContent));
        assertThat(text).contains("Typed text");
    }

    @Test
    void should_perform_custom_actions_within_frame() {
        alice.attemptsTo(
            Open.url(fileUrl("iframe-test.html")),
            WithinFrame.locatedBy("#editor-frame")
                .perform(frame -> {
                    // Custom actions using FrameLocator
                    frame.locator("body#editor").clear();
                    frame.locator("body#editor").fill("Custom action text");
                })
        );

        // Verify the custom action worked
        Target frameContent = Target.the("frame content")
            .inFrame("#editor-frame")
            .locatedBy("body#editor");

        String text = alice.asksFor(Text.of(frameContent));
        assertThat(text).contains("Custom action text");
    }

    @Test
    void should_interact_with_nested_frames() {
        alice.attemptsTo(
            Open.url(fileUrl("nested-frames.html"))
        );

        // Read text from bottom frame
        Target bottomFrameContent = Target.the("bottom frame content")
            .inFrame("frame[name='frame-bottom']")
            .locatedBy("body");

        String bottomText = alice.asksFor(Text.of(bottomFrameContent));
        assertThat(bottomText).containsIgnoringCase("bottom");

        // Read text from left frame (nested inside frame-top)
        Target leftFrameContent = Target.the("left frame content")
            .inFrame("frame[name='frame-top']")
            .inFrame("frame[name='frame-left']")
            .locatedBy("body");

        String leftText = alice.asksFor(Text.of(leftFrameContent));
        assertThat(leftText).containsIgnoringCase("left");
    }

    @Test
    void should_handle_multiple_frame_interactions_in_sequence() {
        alice.attemptsTo(
            Open.url(fileUrl("iframe-test.html")),
            // First interaction
            WithinFrame.locatedBy("#editor-frame")
                .clear("body#editor"),
            // Second interaction
            WithinFrame.locatedBy("#editor-frame")
                .fill("body#editor", "First entry"),
            // Third interaction
            WithinFrame.locatedBy("#editor-frame")
                .clear("body#editor"),
            // Fourth interaction
            WithinFrame.locatedBy("#editor-frame")
                .fill("body#editor", "Second entry")
        );

        // Verify final state
        Target frameContent = Target.the("frame content")
            .inFrame("#editor-frame")
            .locatedBy("body#editor");

        String text = alice.asksFor(Text.of(frameContent));
        assertThat(text).contains("Second entry");
        assertThat(text).doesNotContain("First entry");
    }
}