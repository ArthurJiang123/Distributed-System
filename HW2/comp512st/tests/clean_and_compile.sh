
# Clean the project by removing all .class files
cd ..

echo "Cleaning previous compiled java files"
find . -name "*.class" -type f -delete

cd ..

echo "Compiling script"
./build_tiapp.sh
