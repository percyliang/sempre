NAME=sempre

default: $(NAME).jar

DEPS := $(shell ls lib/*.jar) $(shell find src -name "*.java")

classes: $(DEPS)
	mkdir -p classes
	javac -d classes -cp 'lib/*' -Xlint:all `find src -name "*.java"`
	touch classes

$(NAME).jar: classes
	jar cf $(NAME).jar -C classes .
	jar uf $(NAME).jar -C src .

clean:
	rm -rf classes $(NAME).jar
