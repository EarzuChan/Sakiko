cd native

rm -rf cmake-build-release
mkdir cmake-build-release
cd cmake-build-release

echo "开始配置构建"
cmake -DCMAKE_BUILD_TYPE=Release -DCMAKE_CACHEFILE_DIR=$PWD -Dcross_triple=$CROSS ..
echo "开始构建本机库"
cmake --build . --target native -- -j 3

ecode=$?

mkdir bin

cp *.dll bin
cp *.so bin
cp *.dylib bin

exit $ecode