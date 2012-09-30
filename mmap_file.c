/*
robot36 - encode and decode images using SSTV in Robot 36 mode
Written in 2011 by <Ahmet Inan> <xdsopl@googlemail.com>
To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
*/


#include <stdio.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <unistd.h>
#include "mmap_file.h"

int mmap_file_ro(void **p, char *name, size_t *size)
{
	*size = 0;
	int fd = open(name, O_RDONLY);
	if (fd == -1) {
		perror("open");
		return 0;
	}

	struct stat sb;
	if (fstat(fd, &sb) == -1) {
		perror("fstat");
		close(fd);
		return 0;
	}

	if (!S_ISREG(sb.st_mode)) {
		fprintf(stderr, "%s not a file\n", name);
		close(fd);
		return 0;
	}

	*p = mmap(0, sb.st_size, PROT_READ, MAP_SHARED, fd, 0);
	if (*p == MAP_FAILED) {
		perror("mmap");
		close(fd);
		return 0;
	}

	if (close(fd) == -1) {
		perror ("close");
		return 0;
	}
	*size = sb.st_size;
	return 1;
}

int mmap_file_rw(void **p, char *name, size_t size)
{
	int fd = open(name, O_RDWR|O_CREAT|O_TRUNC, S_IRUSR|S_IWUSR|S_IRGRP|S_IROTH);
	if (fd == -1) {
		perror("open");
		return 0;
	}

	struct stat sb;
	if (fstat(fd, &sb) == -1) {
		perror("fstat");
		close(fd);
		return 0;
	}

	if (!S_ISREG(sb.st_mode)) {
		fprintf(stderr, "%s not a file\n", name);
		close(fd);
		return 0;
	}

	if (lseek(fd, size - 1, SEEK_SET) == -1) {
		perror("lseek");
		close(fd);
		return 0;
	}

	if (write(fd, "", 1) != 1) {
		perror("write");
		close(fd);
		return 0;
	}

	*p = mmap(0, size, PROT_READ|PROT_WRITE, MAP_SHARED, fd, 0);
	if (*p == MAP_FAILED) {
		perror("mmap");
		close(fd);
		return 0;
	}

	if (close(fd) == -1) {
		perror ("close");
		return 0;
	}
	return 1;
}
int munmap_file(void *p, size_t size)
{
	if (munmap(p, size) == -1) {
		perror("munmap");
		return 0;
	}
	return 1;
}

