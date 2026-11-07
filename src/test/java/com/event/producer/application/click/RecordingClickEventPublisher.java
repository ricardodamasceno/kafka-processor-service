package com.event.producer.application.click;

import com.event.producer.application.shared.PublishFailedException;
import com.event.producer.domain.click.Click;

import java.util.ArrayList;
import java.util.List;

/** Test double for the publisher port: remembers what was published, and can simulate a broker failure. */
class RecordingClickEventPublisher implements ClickEventPublisher {

    final List<Click> published = new ArrayList<>();
    boolean failing;

    @Override
    public void publishCreationRequested(Click click) {
        if (failing) {
            throw new PublishFailedException("broker unavailable", new RuntimeException());
        }
        published.add(click);
    }
}
