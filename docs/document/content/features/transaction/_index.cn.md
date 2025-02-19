+++
pre = "<b>3.2. </b>"
title = "分布式事务"
weight = 2
chapter = true
+++

## 背景

数据库事务需要满足 ACID（原子性、一致性、隔离性、持久性）四个特性。

- 原子性（Atomicity）指事务作为整体来执行，要么全部执行，要么全不执行；
- 一致性（Consistency）指事务应确保数据从一个一致的状态转变为另一个一致的状态；
- 隔离性（Isolation）指多个事务并发执行时，一个事务的执行不应影响其他事务的执行；
- 持久性（Durability）指已提交的事务修改数据会被持久保存。

在单一数据节点中，事务仅限于对单一数据库资源的访问控制，称之为本地事务。 几乎所有的成熟的关系型数据库都提供了对本地事务的原生支持。 但是在基于微服务的分布式应用环境下，越来越多的应用场景要求对多个服务的访问及其相对应的多个数据库资源能纳入到同一个事务当中，分布式事务应运而生。

关系型数据库虽然对本地事务提供了完美的 ACID 原生支持。 但在分布式的场景下，它却成为系统性能的桎梏。 如何让数据库在分布式场景下满足 ACID 的特性或找寻相应的替代方案，是分布式事务的重点工作。

## 挑战

由于应用的场景不同，需要开发者能够合理的在性能与功能之间权衡各种分布式事务。

强一致的事务与柔性事务的 API 和功能并不完全相同，在它们之间并不能做到自由的透明切换。 在开发决策阶段，就不得不在强一致的事务和柔性事务之间抉择，使得设计和开发成本被大幅增加。

基于 XA 的强一致事务使用相对简单，但是无法很好的应对互联网的高并发或复杂系统的长事务场景； 柔性事务则需要开发者对应用进行改造，接入成本非常高，并且需要开发者自行实现资源锁定和反向补偿。

## 目标

整合现有的成熟事务方案，为本地事务、两阶段事务和柔性事务提供统一的分布式事务接口，并弥补当前方案的不足，提供一站式的分布式事务解决方案是 Apache ShardingSphere 分布式事务模块的主要设计目标。

## 原理介绍

ShardingSphere 对外提供 begin/commit/rollback 传统事务接口，通过 LOCAL，XA，BASE 三种模式提供了分布式事务的能力，

### LOCAL 事务

LOCAL 模式基于 ShardingSphere 代理的数据库 `begin/commit/rolllback` 的接口实现，
对于一条逻辑 SQL，ShardingSphere 通过 `begin` 指令在每个被代理的数据库开启事务，并执行实际 SQL，并执行 `commit/rollback`。
由于每个数据节点各自管理自己的事务，它们之间没有协调以及通信的能力，也并不互相知晓其他数据节点事务的成功与否。
在性能方面无任何损耗，但在强一致性以及最终一致性方面不能够保证。

### XA 事务

XA 事务采用的是 X/OPEN 组织所定义的 [DTP 模型](http://pubs.opengroup.org/onlinepubs/009680699/toc.pdf) 所抽象的 AP（应用程序）, TM（事务管理器）和 RM（资源管理器） 概念来保证分布式事务的强一致性。
其中 TM 与 RM 间采用 XA 的协议进行双向通信，通过两阶段提交实现。
与传统的本地事务相比，XA 事务增加了准备阶段，数据库除了被动接受提交指令外，还可以反向通知调用方事务是否可以被提交。
`TM` 可以收集所有分支事务的准备结果，并于最后进行原子提交，以保证事务的强一致性。

![两阶段提交模型](https://shardingsphere.apache.org/document/current/img/transaction/overview.png)

XA 事务建立在 ShardingSphere 代理的数据库 xa start/end/prepare/commit/rollback/recover 的接口上。

对于一条逻辑 SQL，ShardingSphere 通过 `xa begin` 指令在每个被代理的数据库开启事务，内部集成 TM，用于协调各分支事务，并执行 `xa commit/rollback`。

基于 XA 协议实现的分布式事务，由于在执行的过程中需要对所需资源进行锁定，它更加适用于执行时间确定的短事务。
对于长事务来说，整个事务进行期间对数据的独占，将会对并发场景下的性能产生一定的影响。

### BASE 事务

如果将实现了 ACID 的事务要素的事务称为刚性事务的话，那么基于 BASE 事务要素的事务则称为柔性事务。
BASE 是基本可用、柔性状态和最终一致性这三个要素的缩写。

- 基本可用（Basically Available）保证分布式事务参与方不一定同时在线；
- 柔性状态（Soft state）则允许系统状态更新有一定的延时，这个延时对客户来说不一定能够察觉；
- 最终一致性（Eventually consistent）通常是通过消息传递的方式保证系统的最终一致性。

在 ACID 事务中对隔离性的要求很高，在事务执行过程中，必须将所有的资源锁定。
柔性事务的理念则是通过业务逻辑将互斥锁操作从资源层面上移至业务层面。
通过放宽对强一致性要求，来换取系统吞吐量的提升。

基于 ACID 的强一致性事务和基于 BASE 的最终一致性事务都不是银弹，只有在最适合的场景中才能发挥它们的最大长处。
Apache ShardingSphere 集成了 SEATA 作为柔性事务的使用方案。
可通过下表详细对比它们之间的区别，以帮助开发者进行技术选型。

|          | *LOCAL*       | *XA*              | *BASE*     |
| -------- | ------------- | ---------------- | ------------ |
| 业务改造  | 无             | 无               | 需要 seata server|
| 一致性    | 不支持         | 支持             | 最终一致       |
| 隔离性    | 不支持         | 支持             | 业务方保证     |
| 并发性能  | 无影响         | 严重衰退          | 略微衰退       |
| 适合场景  | 业务方处理不一致 | 短事务 & 低并发   | 长事务 & 高并发 |

## 应用场景

在单机应用场景中，依赖数据库提供的事务即可满足业务上对事务 ACID 的需求。但是在分布式场景下，传统数据库解决方案缺乏对全局事务的管控能力，用户在使用过程中可能遇到多个数据库节点上出现数据不一致的问题。

ShardingSphere 分布式事务，为用户屏蔽了分布式事务处理的复杂性，提供了灵活多样的分布式事务解决方案，用户可以根据自己的业务场景在 LOCAL，XA，BASE 三种模式中，选择适合自己的分布式事务解决方案。

### ShardingSphere XA 事务使用场景

对于 XA 事务，提供了分布式环境下，对数据强一致性的保证。但是由于存在同步阻塞问题，对性能会有一定影响。适用于对数据一致性要求非常高且对并发性能要求不是很高的业务场景。

### ShardingSphere BASE 事务使用场景

对于 BASE 事务，提供了分布式环境下，对数据最终一致性的保证。由于在整个事务过程中，不会像 XA 事务那样全程锁定资源，所以性能较好。适用于对并发性能要求很高并且允许出现短暂数据不一致的业务场景。

### ShardingSphere LOCAL事务使用场景

对于 LOCAL 事务，在分布式环境下，不保证各个数据库节点之间数据的一致性和隔离性，需要业务方自行处理可能出现的不一致问题。适用于用户希望自行处理分布式环境下数据一致性问题的业务场景。

## 相关参考

- [分布式事务的 YAML 配置](/cn/user-manual/shardingsphere-jdbc/yaml-config/rules/transaction/)
