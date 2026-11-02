#ifndef EDUCATION_NATIVE_MATH_H
#define EDUCATION_NATIVE_MATH_H

/*
 * Explicit native ABI contracts. No C++ overloading or platform-specific
 * headers: jextract can generate stable bindings for these two C symbols.
 */
int edu_square(int value);
int edu_add(int left, int right);

#endif
