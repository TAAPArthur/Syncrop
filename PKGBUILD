# Maintainer: Arthur Williams <taaparthur@gmail.com>

pkgname='syncrop'
pkgver='2.3.8'
_language='en-US'
pkgrel=1
pkgdesc='SyncROP syncs your files to a cloud and access them form anywhere.'

arch=('any')
license=('MIT')
depends=('java-runtime-headless')
optdepends=('python: tray-icon','pyqt4-common: tray-icon')
md5sums=('SKIP')

source=("https://github.com/TAAPArthur/Syncrop/releases/download/v$pkgver/syncrop.tar.gz")
_srcDir="fakeroot"

package() {

  cd "$_srcDir"
  mv * "$pkgdir"

}
