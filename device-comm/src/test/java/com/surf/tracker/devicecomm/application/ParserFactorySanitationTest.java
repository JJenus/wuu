package com.surf.tracker.devicecomm.application;

import com.surf.tracker.devicecomm.exception.ProtocolException;
import com.surf.tracker.devicecomm.infrastructure.AutoseekerProtocolParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class ParserFactorySanitationTest {
    @Test
    void getParser_oneCharacter_rejectsAsProtocolError() {
        // given
        var factory = new ParserFactory(List.of(new AutoseekerProtocolParser()));
        // when / then
        assertThatThrownBy(() -> factory.getParser("*")).isInstanceOf(ProtocolException.class);
    }

    @Test
    void getParser_untrustedPayload_neverEchoesIntoLogsOrExceptions(CapturedOutput output) {
        // given
        var factory = new ParserFactory(List.of(new AutoseekerProtocolParser()));
        String marker = "UNTRUSTED-CONTROL-MARKER";
        // when / then
        assertThatThrownBy(() -> factory.getParser("\r\n" + marker + "\u001b"))
            .isInstanceOf(ProtocolException.class).hasMessageNotContaining(marker);
        assertThat(output.getAll()).doesNotContain(marker);
    }
}
