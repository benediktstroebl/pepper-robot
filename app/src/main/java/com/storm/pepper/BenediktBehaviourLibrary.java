package com.storm.pepper;

import com.aldebaran.qi.Future;
import com.aldebaran.qi.sdk.QiContext;
import com.aldebaran.qi.sdk.builder.AnimateBuilder;
import com.aldebaran.qi.sdk.builder.AnimationBuilder;
import com.aldebaran.qi.sdk.builder.ChatBuilder;
import com.aldebaran.qi.sdk.builder.GoToBuilder;
import com.aldebaran.qi.sdk.builder.ListenBuilder;
import com.aldebaran.qi.sdk.builder.PhraseSetBuilder;
import com.aldebaran.qi.sdk.builder.QiChatbotBuilder;
import com.aldebaran.qi.sdk.builder.SayBuilder;
import com.aldebaran.qi.sdk.builder.TopicBuilder;
import com.aldebaran.qi.sdk.builder.TransformBuilder;
import com.aldebaran.qi.sdk.object.actuation.Animate;
import com.aldebaran.qi.sdk.object.actuation.Animation;
import com.aldebaran.qi.sdk.object.actuation.Frame;
import com.aldebaran.qi.sdk.object.actuation.FreeFrame;
import com.aldebaran.qi.sdk.object.actuation.GoTo;
import com.aldebaran.qi.sdk.object.conversation.BodyLanguageOption;
import com.aldebaran.qi.sdk.object.conversation.Chat;
import com.aldebaran.qi.sdk.object.conversation.Listen;
import com.aldebaran.qi.sdk.object.conversation.ListenResult;
import com.aldebaran.qi.sdk.object.conversation.Phrase;
import com.aldebaran.qi.sdk.object.conversation.PhraseSet;
import com.aldebaran.qi.sdk.object.conversation.QiChatbot;
import com.aldebaran.qi.sdk.object.conversation.Say;
import com.aldebaran.qi.sdk.object.conversation.Topic;
import com.aldebaran.qi.sdk.object.geometry.Transform;
import com.aldebaran.qi.sdk.object.human.Human;
import com.aldebaran.qi.sdk.object.humanawareness.HumanAwareness;
import com.aldebaran.qi.sdk.util.FutureUtils;
import com.storm.posh.BaseBehaviourLibrary;
import com.storm.posh.plan.planelements.Sense;
import com.storm.posh.plan.planelements.action.ActionEvent;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.Random;

public class BenediktBehaviourLibrary extends BaseBehaviourLibrary {
    private static final String TAG = BenediktBehaviourLibrary.class.getSimpleName();

    private boolean doNotAnnoy = false;
    private boolean heardStop = false;
    private boolean haveWavedLeft = false;
    private boolean haveWavedRight = false;
//    private boolean interactionTimerSet = false; TODO: uncomment for fixed seconds interrupt timer
    private boolean turnedAround = false;

    private int currentDistanceLvl;

    private String[] humPhrases = {"I am bored", "I wish something was happening", "This is boring, please approach me."};
    private Date nextHumTime;
    private Date nextGreetTime;
    private Date nextRoamTime;
//    private Date interruptTime; TODO: uncomment for fixed seconds interrupt timer
    private Date lastTimeTalk;

    private GoTo goTo;


    private Future<ListenResult> listenFuture;
    private Future<Void> chatFuture;

    public BenediktBehaviourLibrary() {
        setInstance();
    }

    public void reset() {
        super.reset();

        haveWavedLeft = false;
        haveWavedRight = false;
        doNotAnnoy = false;
        heardStop = false;
//        interactionTimerSet = false; TODO: uncomment for fixed seconds interrupt timer
        turnedAround = false;
    }

    public boolean getBooleanSense(Sense sense) {
//        pepperLog.appendLog(TAG, String.format("Getting boolean sense: %s", sense));
        boolean senseValue;

        switch (sense.getNameOfElement()) {
            case "HaveWavedLeft":
                senseValue = haveWavedLeft;
                break;
            case "HaveWavedRight":
                senseValue = haveWavedRight;
                break;
            case "HeardStop":
                senseValue = heardStop;
                break;
            case "DoNotAnnoy":
                senseValue = doNotAnnoy;
                break;
            case "ReadyToGreet":
                senseValue = getReadyToGreet();
                break;
            case "ReadyToInterrupt":
                senseValue = getReadyToInterrupt();
                break;
            case "TurnedAround":
                senseValue = turnedAround;
                break;
            default:
                senseValue = super.getBooleanSense(sense);
                break;
        }

        pepperLog.checkedBooleanSense(TAG, sense, senseValue);

        return senseValue;
    }

    public double getDoubleSense(Sense sense) {
        return super.getDoubleSense(sense);
    }

    public void executeAction(ActionEvent action) {
        pepperLog.appendLog(TAG, "Performing action: " + action);

        switch (action.getNameOfElement()) {
            case "WaveLeft":
                waveLeft();
                break;

            case "WaveRight":
                waveRight();
                break;

            case "ClearWaving":
                clearWaving();
                break;

            case "ApproachHuman":
                approachHuman();
                break;

            case "ListenForStop":
                listenForStop();
                break;

            case "ListenForUser":
                startChat();
                break;

            case "DoNotAnnoy":
                this.doNotAnnoy = true;
                break;

            case "ForgetAnnoy":
                this.doNotAnnoy = false;
//                this.interactionTimerSet = false; TODO: uncomment for fixed seconds interrupt timer
//                this.interruptTime = null;
                this.turnedAround = false;
                this.currentDistanceLvl = 99;
                this.lastTimeTalk = null;
                break;

            case "Hum":
                hum();
                break;

            case "Roam":
                roam();
                break;

            case "Greet":
                greet();
                break;

            case "StopListening":
                chatFuture.requestCancellation();
                break;

            case "Interrupt":
                this.doNotAnnoy = true;
                sayBye();
                break;

            case "TurnAround":
                turnAroundAndGo();
                break;

            default:
                super.executeAction(action);
                break;
        }
    }

    public void hum() {
        Date now = new Date();
        if (talking) {
            pepperLog.appendLog(TAG, "Already talking, can't hum now");
            return;
        } else if (nextHumTime != null && now.before(nextHumTime)) {
            pepperLog.appendLog(TAG, "Don't want to hum yet");
            return;
        } else {
            pepperLog.appendLog(TAG, "Bored, gonna hum!");
        }

        // set next time to hum
        int humDelay = ThreadLocalRandom.current().nextInt(8, 15);
        pepperLog.appendLog(TAG, String.format("Next hum in %d seconds", humDelay));
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);
        calendar.add(Calendar.SECOND, humDelay);
        nextHumTime = calendar.getTime();

        // choose a phrase to hum
        int index = ThreadLocalRandom.current().nextInt(humPhrases.length);
        System.out.println("\nIndex :" + index);
        String humPhrase = humPhrases[index];

        FutureUtils.wait(0, TimeUnit.SECONDS).andThenConsume((ignore) -> {
            this.talking = true;
            Say say = SayBuilder.with(qiContext) // Create the builder with the context.
                    .withText(humPhrase) // Set the text to say.
                    .withBodyLanguageOption(BodyLanguageOption.DISABLED)
                    .build(); // Build the say action.

            say.run();

            this.talking = false;
        });
    }

    public boolean getReadyToGreet () {
        Date now = new Date();
        if (nextGreetTime != null && !now.before(nextGreetTime)) {
            pepperLog.appendLog(TAG, "I can greet again");
            return true;
        } else if (nextGreetTime == null) {
            pepperLog.appendLog(TAG, "Never greeted before, let me greet");
            return true;
        } else {
            return false;
        }
    }

    public boolean getReadyToInterrupt () {
        Date now = new Date();

        if (this.lastTimeTalk != null) {
            long seconds_since_last_talk = Math.abs(now.getTime() - this.lastTimeTalk.getTime()) / 1000;
            if (seconds_since_last_talk > 20) {
                pepperLog.appendLog(TAG, "More than 20 secs since I last heard something, going to interrupt...");
                return true;
            } else {
                pepperLog.appendLog(TAG, "Not ready to interrupt yet");
                return false;
            }
        } else {
            pepperLog.appendLog(TAG, "No last time I heard something set yet");
            return false;
        }

//        TODO: uncomment for fixed seconds interrupt timer
//        if (interruptTime != null && now.before(interruptTime) && this.interactionTimerSet) {
//            pepperLog.appendLog(TAG, "I am not ready to interrupt yet");
//            return false;
//        } else if (interruptTime != null && !now.before(interruptTime) && this.interactionTimerSet){
//            pepperLog.appendLog(TAG, "It is about time to interrupt this interaction");
//            return true;
//        } else {
//            pepperLog.appendLog(TAG, "Weird thing with interruption timer is happening.");
//
//            this.interactionTimerSet = true;
//
//            pepperLog.appendLog(TAG, "Setting interruption timer...");
//            // set time to interrupt interaction
//            int interactDelay = 45; // interrupt interaction 45 secs after first contact
//            pepperLog.appendLog(TAG, String.format("Will interrupt this interaction in %d seconds", interactDelay));
//            Calendar calendar_interrupt = Calendar.getInstance();
//            calendar_interrupt.setTime(now);
//            calendar_interrupt.add(Calendar.SECOND, interactDelay);
//            interruptTime = calendar_interrupt.getTime();
//
//            pepperLog.appendLog(TAG, "Interruption timer set successfully.");
//
//            return false;
//        }
    }

    public void startChat () {
        if (listening) {
            pepperLog.appendLog(TAG, "Already chatting");
            return;
        } else if (heardStop) {
            pepperLog.appendLog(TAG, "Already heard stop");
            return;
        } else {
            setActive();
        }

        FutureUtils.wait(0, TimeUnit.SECONDS).andThenConsume((ignore) -> {

            // Create a topic.
            Topic topic = TopicBuilder.with(qiContext) // Create the builder using the QiContext.
                    .withResource(R.raw.greetings) // Set the topic resource.
                    .build(); // Build the topic.

            // Create a new QiChatbot.
            QiChatbot qiChatbot = QiChatbotBuilder.with(qiContext)
                    .withTopic(topic)
                    .build();

            // Create a new Chat action.
            this.chat = ChatBuilder.with(qiContext)
                    .withChatbot(qiChatbot)
                    .build();

            this.chat.addOnStartedListener(() -> {
                this.listening = true;
                pepperLog.appendLog(TAG, "Started chat");
            });

            this.chat.addOnHeardListener(heard -> {
                Date now = new Date();
                this.lastTimeTalk = now;
                pepperLog.appendLog(TAG, "I heard something");
            });

            FutureUtils.wait(0, TimeUnit.SECONDS).andThenConsume((ignore_2) -> {
                this.chatFuture = chat.async().run();

                chatFuture.thenConsume(future -> {
                    this.listening = false;
                    if (future.hasError()) {
                        pepperLog.appendLog(TAG, "Chat ended with error: " + future.getErrorMessage());
                    } else if (future.isCancelled()) {
                        pepperLog.appendLog(TAG, "Chat has been cancelled");
                    }
                });
            });

        });
    }

    public void listenForStop() {
        pepperLog.appendLog(TAG, "Listen for stop?");
        if (listening) {
            pepperLog.appendLog(TAG, "Already listening");
            return;
        } else if (heardStop) {
            pepperLog.appendLog(TAG, "Already heard stop");
            return;
        } else {
            setActive();
        }


        pepperLog.appendLog(TAG, "1");

        FutureUtils.wait(0, TimeUnit.SECONDS).andThenConsume((ignore) -> {
            // TODO: Only init this once
            pepperLog.appendLog(TAG, "2");
            PhraseSet phraseSet = PhraseSetBuilder.with(qiContext).withTexts("Stop", "Go Away", "No").build();
            pepperLog.appendLog(TAG, "3");
            Listen listen = ListenBuilder.with(qiContext).withPhraseSet(phraseSet).build();

            listen.addOnStartedListener(() -> {
                this.listening = true;
                pepperLog.appendLog("Started listening...");
                pepperLog.appendLog(TAG, "4");
            });

            this.listenFuture = listen.async().run();

            listenFuture.thenConsume(future -> {
                this.listening = false;
                pepperLog.appendLog(TAG, "5");
                handleFuture(future, "listen_for_stop");

                try {
                    ListenResult result = future.get();

                    PhraseSet heardPhraseSet = result.getMatchedPhraseSet();
                    Phrase heardPhrase = result.getHeardPhrase();

                    pepperLog.appendLog(TAG, String.format("Phrase was: %s", heardPhrase));

                    if (heardPhrase.getText().equals("Stop")) {
                        this.heardStop = true;
                        listenFuture.requestCancellation();
                        pepperLog.appendLog(TAG, "Heard Stop");
                    }

                } catch (ExecutionException e) {
                    pepperLog.appendLog(TAG, "Error occurred when listening for stop");
                } catch (CancellationException e) {
                    pepperLog.appendLog(TAG, "Listening for stop was cancelled");
                }
            });
        });
    }

    public void waveLeft() {
        setActive();

        if (animating) {
            pepperLog.appendLog(TAG, "WAVING IN PROGRESS");
            return;
        }

        pepperLog.appendLog(TAG, "WAVING LEFT: starting");
        setAnimating(true);

        // Create an animation object.
        Future<Animation> myAnimationFuture = AnimationBuilder.with(qiContext)
                .withResources(R.raw.left_hand_high_b001)
                .buildAsync();

        myAnimationFuture.andThenConsume(myAnimation -> {
            Animate animate = AnimateBuilder.with(qiContext)
                    .withAnimation(myAnimation)
                    .build();

            // Run the action synchronously in this thread
            animate.run();

            pepperLog.appendLog(TAG, "WAVING LEFT: finished");
            setHaveWavedLeft(true);
            setAnimating(false);
        });

    }

    public void greet() {
        Date now = new Date();
        if (talking) {
            pepperLog.appendLog(TAG, "Already talking, can't greet now");
            return;
        } else if (nextGreetTime != null && now.before(nextGreetTime)) {
            pepperLog.appendLog(TAG, "Don't want to greet yet");
            return;
        } else if (heardStop) {
            pepperLog.appendLog(TAG, "Already heard stop");
            return;
        } else {
            pepperLog.appendLog(TAG, "Gonna greet!");
        }

        // set next time to hum
        int greetDelay = ThreadLocalRandom.current().nextInt(8, 15);
        pepperLog.appendLog(TAG, String.format("Next greet in %d seconds", greetDelay));
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);
        calendar.add(Calendar.SECOND, greetDelay);
        nextGreetTime = calendar.getTime();

        double distance = getDistanceToHumanToGreet();
        int distanceLvl;
        String greeting;

        if (distance > 3) {
            distanceLvl = 3;
            if (distanceLvl == this.currentDistanceLvl) {
                pepperLog.appendLog(TAG, "Distance to human has not changed, I do not need to greet.");
                return;
            }
            this.currentDistanceLvl = 3;
            greeting = "You are far away, please get a little closer!";
        } else if (distance > 2) {
            distanceLvl = 2;
            if (distanceLvl == this.currentDistanceLvl) {
                pepperLog.appendLog(TAG, "Distance to human has not changed, I do not need to greet.");
                return;
            }
            this.currentDistanceLvl = 2;
            greeting = "Get closer so that I can see you better!";
        } else if (distance > 1) {
            distanceLvl = 1;
            if (distanceLvl == this.currentDistanceLvl) {
                pepperLog.appendLog(TAG, "Distance to human has not changed, I do not need to greet.");
                return;
            }
            this.currentDistanceLvl = 1;
            greeting = "One more step and I see you perfectly.";
        } else {
            distanceLvl = 0;
            if (distanceLvl == this.currentDistanceLvl) {
                pepperLog.appendLog(TAG, "Distance to human has not changed, I do not need to greet.");
                return;
            }
            this.currentDistanceLvl = 0;
            greeting = "Hey there!";
        }

        FutureUtils.wait(0, TimeUnit.SECONDS).andThenConsume((ignore) -> {
            this.talking = true;
            Say say = SayBuilder.with(qiContext) // Create the builder with the context.
                    .withText(greeting) // Set the text to say.
                    .withBodyLanguageOption(BodyLanguageOption.DISABLED)
                    .build(); // Build the say action.

            say.run();
            this.talking = false;
        });
    }

    private void sayBye() {
        if (talking) {
            pepperLog.appendLog(TAG, "Already talking, can't say goodbye now");
            return;
        }

        String goodbye_phrase = "I think I walk away now";

        FutureUtils.wait(0, TimeUnit.SECONDS).andThenConsume((ignore) -> {
            this.talking = true;
            Say say = SayBuilder.with(qiContext) // Create the builder with the context.
                    .withText(goodbye_phrase) // Set the text to say.
                    .withBodyLanguageOption(BodyLanguageOption.DISABLED)
                    .build(); // Build the say action.

            say.run();
            this.talking = false;
        });
    }

    private void turnAroundAndGo() {
        if (animating) {
            pepperLog.appendLog(TAG, "Can't turn around. Already animating");
            return;
        }

        FutureUtils.wait(0, TimeUnit.SECONDS).andThenConsume((ignore) -> {
            setAnimating(true);
            // Get the robot frame.
            Frame robotFrame = actuation.robotFrame();

            // Create a FreeFrame representing the current robot frame.
            FreeFrame locationFrame = mapping.makeFreeFrame();

            Transform transform = TransformBuilder.create().from2DTranslation(-0.5, 0.0);
            locationFrame.update(robotFrame, transform, 0L);

            // Create a GoTo action.
            goTo = GoToBuilder.with(qiContext)
                    .withFrame(locationFrame.frame())
                    .build();

            // Display text when the GoTo action starts.
            pepperLog.appendLog(TAG, "Turning around and going...");

            // Execute the GoTo action asynchronously.
            Future<Void> goToFuture = goTo.async().run();
            goToFuture.thenConsume(future -> {
                if (future.isSuccess()) {
                    setAnimating(false);
                    this.turnedAround = true;
                    pepperLog.appendLog(TAG, "Turning around finished with success!");
                } else if (future.hasError()) {
                    pepperLog.appendLog(TAG, "Turning around has error!");
                }
            });
        });


    }

    public void roam() {
        Date now = new Date();
        if (animating) {
            pepperLog.appendLog(TAG, String.format("Already animating, cannot roam"));
            return;
        } else if (nextRoamTime != null && now.before(nextRoamTime)) {
            pepperLog.appendLog(TAG, "Don't want to roam yet");
            return;
        } else {
            pepperLog.appendLog(TAG, "Bored, gonna roam!");
        }

        // set next time to hum
        int roamDelay = ThreadLocalRandom.current().nextInt(15, 30);
        pepperLog.appendLog(TAG, String.format("Next roam in %d seconds", roamDelay));
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);
        calendar.add(Calendar.SECOND, roamDelay);
        nextRoamTime = calendar.getTime();

        FutureUtils.wait(0, TimeUnit.SECONDS).andThenConsume((ignore) -> {
            setAnimating(true);


//            double x = 0.0;
//            double y = 0.0;

            // TODO: uncomment
            Random rand = new Random();
            double x = 3 * rand.nextDouble();
            double y = 3 * rand.nextDouble();

            // Get the robot frame.
            Frame robotFrame = actuation.robotFrame();

            // Create a FreeFrame representing the current robot frame.
            FreeFrame locationFrame = mapping.makeFreeFrame();

            Transform transform = TransformBuilder.create().from2DTranslation(x, y);
            locationFrame.update(robotFrame, transform, 0L);

            // Create a GoTo action.
            goTo = GoToBuilder.with(qiContext)
                    .withFrame(locationFrame.frame())
                    .build();

            // Display text when the GoTo action starts.
            goTo.addOnStartedListener(() -> pepperLog.appendLog(TAG, "Roaming started"));

            // Execute the GoTo action asynchronously.
            Future<Void> goToFuture = goTo.async().run();
            goToFuture.thenConsume(future -> {
                if (future.isSuccess()) {
                    setAnimating(false);
                    pepperLog.appendLog(TAG, "Roaming finished with success!");
                } else if (future.hasError()) {
                    pepperLog.appendLog(TAG, "Roaming has error!");
                }
            });

        });

    }

    private void waveRight() {
        setActive();

        if (animating) {
            pepperLog.appendLog(TAG, "WAVING IN PROGRESS");
            return;
        }

        pepperLog.appendLog(TAG, "WAVING RIGHT: starting");
        setAnimating(true);

        // Create an animation object.
        Future<Animation> myAnimationFuture = AnimationBuilder.with(qiContext)
                .withResources(R.raw.right_hand_high_b001)
                .buildAsync();

        myAnimationFuture.andThenConsume(myAnimation -> {
            Animate animate = AnimateBuilder.with(qiContext)
                    .withAnimation(myAnimation)
                    .build();

            // Run the action synchronously in this thread
            animate.run();

            pepperLog.appendLog(TAG, "WAVING RIGHT: finished");
            setHaveWavedRight(true);
            setAnimating(false);
        });
    }

    public void clearWaving() {
        setHaveWavedLeft(false);
        setHaveWavedRight(false);
        pepperLog.appendLog(TAG, "WAVING CLEARED");
    }

    // tidy up listeners
    public void removeListeners() {
        super.removeListeners();
    }

    @Override
    public void onRobotFocusGained(QiContext qiContext) {
        super.onRobotFocusGained(qiContext);
    }

    @Override
    public void onRobotFocusLost() {
        // Remove on started listeners from the GoTo action.
        if (goTo != null) {
            goTo.removeAllOnStartedListeners();
        }
        super.onRobotFocusLost();
    }

    @Override
    public void onRobotFocusRefused(String reason) {
        // The robot focus is refused.
        super.onRobotFocusRefused(reason);
    }

    public void setHaveWavedLeft(boolean state) {
        this.haveWavedLeft = state;
    }

    public void setHaveWavedRight(boolean state) {
        this.haveWavedRight = state;
    }

}
