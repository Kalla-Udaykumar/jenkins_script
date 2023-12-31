set -x
# Set up paths to dev, RT, and LTS directories.
mkdir ${WORKSPACE}/abi/dev_path
mkdir ${WORKSPACE}/abi/lts_path
dev_path=${WORKSPACE}/abi/dev_path
rt_path=${WORKSPACE}/abi/rt_path
lts_path=${WORKSPACE}/abi/lts_path
rt_kernel=${kernel_rt}
lts_kernel=${kernel_lts}

echo ${rt_kernel}
echo ${lts_kernel}

dev_clone="git clone https://github.com/intel-innersource/drivers.ethernet.time-sensitive-networking.ese-linux-net-next.git -b dev/intel-mainline-tracking-6.5-rt5/master"
rt_clone="git clone https://github.com/intel-innersource/os.linux.kernel.mainline-tracking-staging.git -b mainline-tracking-rt/v6.5-rt5"
get_config="wget http://oak-07.jf.intel.com/ikt_kernel_deb_repo/pool/main/l/linux-6.4-rt6-mainline-tracking-rt-230816t044845z/kernel.config"
dev_fullpath="${dev_path}/drivers.ethernet.time-sensitive-networking.ese-linux-net-next"
rt_fullpath="${rt_path}/os.linux.kernel.mainline-tracking-staging"

echo $PWD

echo "========== git clone dev repo =========="
cd ${dev_path}
${dev_clone}

echo "========== git clone IKT RT repo =========="
cd ${rt_path}
${rt_clone}

echo "========== get RT config =========="
cd ${rt_path}
${get_config}

echo "========== apply patches for IKT RT repo =========="
i=0
skip=0
num=99

cd ${rt_fullpath}
mkdir patches

while [ "$i" -lt 20 ]
do
	cd ${dev_fullpath}
	commit_title=$(git log -1 --skip $skip --pretty=format:"%s")
	commit_title=${commit_title//\"/\\\"} #handle slash
	commit_title=${commit_title//"*"/\\"*"} #handle asterisk

	cd ${rt_fullpath}
	search_result=$(git log --grep="${commit_title}" -1 --oneline)
	
	if [ -z "$search_result" ] 
	then
		echo "${commit_title}"
		cd ${dev_fullpath}
		commit_id=$(git log -1 --skip $skip --pretty=format:"%h")
		format_title=${commit_title// /-}
		format_title=${format_title////-}
		format_title=${format_title//:/}
		git format-patch -1 ${commit_id} --stdout > ${rt_fullpath}/patches/${num}-${format_title}.patch
		num=`expr $num - 1`
		i=0
	else
		i=`expr $i + 1`
	fi

	skip=`expr $skip + 1`
done

if [ $num -eq 99 ] 
then
	echo "No new patch!"
else
	cd ${rt_fullpath}
	git am --whitespace=nowarn patches/*.patch
fi

echo "========== build RT kernel =========="
cd ${rt_fullpath}
cp ../$(ls .. | grep .config) .config
sed -i -e "s/CONFIG_LOCALVERSION=.*/CONFIG_LOCALVERSION=\"-ci-automation\"/" .config
scripts/config --disable DEBUG_INFO
make olddefconfig && make -j12 bindeb-pkg
rm ../linux-image-*-dbg_*.deb
tar -czvf release_rt.tar.gz ../*.deb
