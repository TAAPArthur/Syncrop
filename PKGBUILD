# Maintainer: Arthur Williams <taaparthur@gmail.com>

pkgname='syncrop'
pkgver='2.3.7'
_language='en-US'
pkgrel=1
pkgdesc='SyncROP syncs your files to a cloud and access them form anywhere.'

arch=('any')
license=('MIT')
depends=('java-runtime')
md5sums=('SKIP')

source=("https://github.com/TAAPArthur/Syncrop/syncrop.tar.gz")
_srcDir="fakeroot"

package() {

  cd "$_srcDir"
  mkdir -p "$pkgdir/usr/bin/"
  mkdir -p "$pkgdir/usr/lib/$pkgname/"
  install -D -m 0755 * "$pkgdir/"

}
