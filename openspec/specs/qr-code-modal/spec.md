# qr-code-modal Specification

## Purpose

Covers the frontend's QR-code modal, which shows the game's join link as a QR code, and makes sure
opening and closing it repeatedly behaves the same every time.

## Requirements

### Requirement: QR modal closes cleanly
The QR modal SHALL close when the user clicks an element marked `data-close-modal` (backdrop or
Close button) or presses Escape. Each open/close cycle SHALL leave no extra event listeners behind:
after closing, a click on a `data-close-modal` element SHALL NOT call the close handler again.

#### Scenario: Close via button
- **WHEN** the modal is open and the user clicks the Close button
- **THEN** the modal gets `aria-hidden="true"` and loses the `open` class

#### Scenario: Repeated open/close does not accumulate listeners
- **WHEN** the user opens and closes the modal several times, and then clicks the backdrop while the modal is closed
- **THEN** the close handler is not called
