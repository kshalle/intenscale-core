TOPDIR = ..
MKDIR = $(TOPDIR)/Make

#the default target cannot start with .
default: .autoheaders.h
 
.autoheaders.h: suN.h suN_types.h suN_repr_func.h
	echo done > $@

include $(MKDIR)/MkRules

suN.h suN_types.h: $(MKDIR)/MkFlags su$(NG).h su$(NG)_types.h
	echo -n "Writing suN headers..."
	cp $(TOPDIR)/Include/su$(NG).h $(TOPDIR)/Include/suN.h &&\
	cp $(TOPDIR)/Include/su$(NG)_types.h $(TOPDIR)/Include/suN_types.h &&\
		echo " done." || ( echo " failed." && false )

suN_repr_func.h: $(MKDIR)/MkFlags su$(NG)_repr_func.h
	echo -n "Writing representation header..."
	cp su$(NG)_repr_func.h suN_repr_func.h &&\
		echo " done." || ( echo " failed." && false )


clean::
	rm -f *~ suN.h suN_types.h suN_repr_func.h .autoheaders.h


.PHONY: default

