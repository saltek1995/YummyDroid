'use strict';

const context = cast.framework.CastReceiverContext.getInstance();
const controls = cast.framework.ui.Controls.getInstance();

function configureControls() {
    controls.clearDefaultSlotAssignments();
    controls.assignButton(
        cast.framework.ui.ControlsSlot.SLOT_SECONDARY_1,
        cast.framework.ui.ControlsButton.QUEUE_PREV,
    );
    controls.assignButton(
        cast.framework.ui.ControlsSlot.SLOT_PRIMARY_1,
        cast.framework.ui.ControlsButton.CAPTIONS,
    );
    controls.assignButton(
        cast.framework.ui.ControlsSlot.SLOT_PRIMARY_2,
        cast.framework.ui.ControlsButton.SEEK_FORWARD_30,
    );
    controls.assignButton(
        cast.framework.ui.ControlsSlot.SLOT_SECONDARY_2,
        cast.framework.ui.ControlsButton.QUEUE_NEXT,
    );
}

configureControls();

const receiverOptions = new cast.framework.CastReceiverOptions();
const playbackConfig = new cast.framework.PlaybackConfig();
playbackConfig.autoResumeDuration = 5;
receiverOptions.playbackConfig = playbackConfig;
receiverOptions.supportedCommands =
    cast.framework.messages.Command.ALL_BASIC_MEDIA |
    cast.framework.messages.Command.QUEUE_PREV |
    cast.framework.messages.Command.QUEUE_NEXT |
    cast.framework.messages.Command.STREAM_TRANSFER;

context.start(receiverOptions);
