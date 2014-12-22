BUILD_DEPS = libsempre/sempre-core.jar \
	libsempre/sempre-cache.jar \
	libsempre/sempre-freebase.jar \
	libsempre/sempre-fbalignment.jar \
	libsempre/sempre-paraphrase.jar \
	libsempre/sempre-corenlp.jar \
	libsempre/sempre-jungle.jar


default: module-classes $(BUILD_DEPS)

module-classes:
	scripts/extract-module-classes.rb

core: libsempre/sempre-core.jar
libsempre/sempre-core.jar: \
		$(shell ls src/edu/stanford/nlp/sempre/*.java) \
		$(shell ls src/edu/stanford/nlp/sempre/test/*.java)
	cd src/edu/stanford/nlp/sempre && ant compile

cache: libsempre/sempre-cache.jar
libsempre/sempre-cache.jar: \
		$(shell find src/edu/stanford/nlp/sempre/cache -name "*.java")
	cd src/edu/stanford/nlp/sempre/cache && ant compile

corenlp: libsempre/sempre-corenlp.jar
libsempre/sempre-corenlp.jar: libsempre/sempre-core.jar libsempre/sempre-cache.jar \
		$(shell find src/edu/stanford/nlp/sempre/corenlp -name "*.java")
	cd src/edu/stanford/nlp/sempre/corenlp && ant compile

freebase: libsempre/sempre-freebase.jar
libsempre/sempre-freebase.jar: libsempre/sempre-core.jar libsempre/sempre-cache.jar \
		$(shell find src/edu/stanford/nlp/sempre/freebase -name "*.java")
	cd src/edu/stanford/nlp/sempre/freebase && ant compile


clean:
	rm -rf classes libsempre
