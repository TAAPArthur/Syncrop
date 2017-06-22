import sys
from PyQt4 import QtGui
import subprocess


class SystemTrayIcon(QtGui.QSystemTrayIcon):

    def __init__(self, icon, parent=None):
        QtGui.QSystemTrayIcon.__init__(self, icon, parent)
        menu = QtGui.QMenu(parent)
        openLogFile = menu.addAction("Open log file")
        openLogFile.activated.connect(self.openLogFile)

        quit = menu.addAction("Quit")
        quit.activated.connect(self.quit)
        self.setContextMenu(menu)
    def openLogFile(self):
        subprocess.run(["xdg-open", "~/.syncrop/syncrop.log"])
    def quit(self):
        sys.exit(0)
def main():
    app = QtGui.QApplication(sys.argv)

    w = QtGui.QWidget()
    trayIcon = SystemTrayIcon(QtGui.QIcon("/usr/share/pixmaps/syncrop.png"), w)
    trayIcon.activated.connect(onClick)
    trayIcon.show()
    sys.exit(app.exec_())
def onClick(reason):
    print (reason)
    if reason == QtGui.QSystemTrayIcon.Trigger:
        subprocess.run("syncrop-gui")
if __name__ == '__main__':
    main()
