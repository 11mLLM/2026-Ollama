from datetime import datetime

from PySide6.QtCore import Qt, QTimer, Signal
from PySide6.QtWidgets import QLabel, QVBoxLayout, QWidget

from config import CHARACTER_NAME, DEFAULT_MOOD, MOOD_FACES

try:
    import objc
    from AppKit import (
        NSStatusWindowLevel,
        NSWindowCollectionBehaviorCanJoinAllSpaces,
        NSWindowCollectionBehaviorFullScreenAuxiliary,
        NSWindowCollectionBehaviorStationary,
    )

    _MACOS_NATIVE = True
except ImportError:
    _MACOS_NATIVE = False


def _pin_to_all_spaces(widget):
    if not _MACOS_NATIVE:
        return
    try:
        view = objc.objc_object(c_void_p=int(widget.winId()))
        window = view.window()
        window.setLevel_(NSStatusWindowLevel)
        window.setHidesOnDeactivate_(False)
        window.setCollectionBehavior_(
            NSWindowCollectionBehaviorCanJoinAllSpaces
            | NSWindowCollectionBehaviorStationary
            | NSWindowCollectionBehaviorFullScreenAuxiliary
        )
    except Exception:
        pass


class CatOverlay(QWidget):
    trigger_requested = Signal()
    pause_toggled = Signal(bool)

    def __init__(self):
        super().__init__()
        self.paused = False
        self.log_visible = False
        self.log_lines = []
        self._drag_offset = None
        self._build_ui()

    def _build_ui(self):
        self.setWindowFlags(Qt.FramelessWindowHint | Qt.WindowStaysOnTopHint | Qt.Tool)
        self.setAttribute(Qt.WA_TranslucentBackground)
        self.setFocusPolicy(Qt.StrongFocus)

        self.bubble = QLabel(f"{MOOD_FACES[DEFAULT_MOOD]} 화면 좀 보자...")
        self.bubble.setWordWrap(True)
        self.bubble.setMaximumWidth(300)
        self.bubble.setStyleSheet(
            "QLabel { background: rgba(255,255,255,236); color:#1a1a1a;"
            " border:2px solid #2b2b2b; border-radius:14px; padding:10px 13px;"
            " font-size:14px; }"
        )

        self.face = QLabel(MOOD_FACES[DEFAULT_MOOD])
        self.face.setAlignment(Qt.AlignRight)
        self.face.setStyleSheet("font-size:58px;")

        self.log = QLabel("")
        self.log.setWordWrap(True)
        self.log.setMaximumWidth(320)
        self.log.setStyleSheet(
            "QLabel { background: rgba(18,18,18,228); color:#d6d6d6;"
            " border-radius:10px; padding:8px 10px; font-size:11px; }"
        )
        self.log.hide()

        layout = QVBoxLayout()
        layout.setContentsMargins(8, 8, 8, 8)
        layout.setSpacing(6)
        layout.addWidget(self.bubble, alignment=Qt.AlignRight)
        layout.addWidget(self.face, alignment=Qt.AlignRight)
        layout.addWidget(self.log, alignment=Qt.AlignRight)
        self.setLayout(layout)

    def showEvent(self, event):
        super().showEvent(event)
        _pin_to_all_spaces(self)
        QTimer.singleShot(0, lambda: _pin_to_all_spaces(self))

    def update_comment(self, scene, comment, mood, help_mode):
        face = MOOD_FACES.get(mood, MOOD_FACES[DEFAULT_MOOD])
        self.face.setText(face)
        self.bubble.setText(("💡 " if help_mode else "") + comment)
        stamp = datetime.now().strftime("%H:%M:%S")
        tag = "도움" if help_mode else mood
        self.log_lines.append(f"[{stamp}] ({tag}) {scene[:70]}\n  -> {comment}")
        self.log_lines = self.log_lines[-8:]
        self.log.setText("\n".join(self.log_lines))
        self.adjustSize()

    def show_error(self, message):
        self.face.setText(MOOD_FACES["걱정"])
        self.bubble.setText(f"(에러: {message[:90]})")
        self.adjustSize()

    def show_status(self, message):
        self.bubble.setText(message)

    def mousePressEvent(self, event):
        if event.button() == Qt.LeftButton:
            self._drag_offset = event.globalPosition().toPoint() - self.frameGeometry().topLeft()

    def mouseMoveEvent(self, event):
        if self._drag_offset is not None:
            self.move(event.globalPosition().toPoint() - self._drag_offset)

    def mouseReleaseEvent(self, event):
        self._drag_offset = None

    def mouseDoubleClickEvent(self, event):
        self.trigger_requested.emit()

    def keyPressEvent(self, event):
        key = event.key()
        if key == Qt.Key_Space:
            self.paused = not self.paused
            self.pause_toggled.emit(self.paused)
            self.show_status(f"({CHARACTER_NAME} 잠듦)" if self.paused else f"({CHARACTER_NAME} 깨어남)")
        elif key == Qt.Key_L:
            self.log_visible = not self.log_visible
            self.log.setVisible(self.log_visible)
            self.adjustSize()
        elif key == Qt.Key_T:
            self.trigger_requested.emit()
        elif key == Qt.Key_Q:
            self.close()
