import sys
import threading

from PySide6.QtCore import QObject, QTimer, Signal
from PySide6.QtWidgets import QApplication

from capture import capture_screen_base64
from config import CAPTURE_INTERVAL_MS
from overlay import CatOverlay
from perceive import perceive
from persona import PersonaEngine


class Pipeline(QObject):
    result_ready = Signal(str, str, str, bool)
    failed = Signal(str)

    def __init__(self):
        super().__init__()
        self.engine = PersonaEngine()
        self.busy = False

    def tick(self):
        if self.busy:
            return
        self.busy = True
        threading.Thread(target=self._run, daemon=True).start()

    def _run(self):
        try:
            image = capture_screen_base64()
            scene = perceive(image)
            result = self.engine.comment(scene)
            self.result_ready.emit(scene, result["comment"], result["mood"], result["help_mode"])
        except Exception as error:
            self.failed.emit(str(error))
        finally:
            self.busy = False


def main():
    app = QApplication(sys.argv)

    overlay = CatOverlay()
    overlay.show()
    screen = app.primaryScreen().availableGeometry()
    overlay.move(screen.right() - overlay.width() - 24, screen.top() + 32)

    pipeline = Pipeline()
    pipeline.result_ready.connect(overlay.update_comment)
    pipeline.failed.connect(overlay.show_error)
    overlay.trigger_requested.connect(pipeline.tick)

    timer = QTimer()
    timer.timeout.connect(pipeline.tick)
    timer.start(CAPTURE_INTERVAL_MS)

    def on_pause(paused):
        if paused:
            timer.stop()
        else:
            timer.start(CAPTURE_INTERVAL_MS)

    overlay.pause_toggled.connect(on_pause)

    pipeline.tick()
    sys.exit(app.exec())


if __name__ == "__main__":
    main()
