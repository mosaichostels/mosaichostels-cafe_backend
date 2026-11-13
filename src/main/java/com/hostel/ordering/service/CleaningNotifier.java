package com.hostel.ordering.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Push hook for the cleaning list. Placeholder bodies only log; the real pushes arrive in a later task. */
@Component
public class CleaningNotifier {

    private static final Logger logger = LoggerFactory.getLogger(CleaningNotifier.class);

    public void listSubmitted(int added) {
        logger.info("Cleaning list submitted: {} new bed(s)", added);
    }

    public void cleaned(String roomName, String cleaner, String ezee) {
        logger.info("Cleaning done: {} by {} (eZee {})", roomName, cleaner, ezee);
    }
}
