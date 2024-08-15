cd native-old

rm -rf build
mkdir build
cd build

echo "开始配置构建"
cmake -DCMAKE_BUILD_TYPE=Release .. -G "MinGW Makefiles"
echo "开始构建本机库"
cmake --build . --target sakiko -- -j 3

ecode=$?

mkdir bin

cp *.dll bin
cp *.so bin
cp *.dylib bin

exit $ecode